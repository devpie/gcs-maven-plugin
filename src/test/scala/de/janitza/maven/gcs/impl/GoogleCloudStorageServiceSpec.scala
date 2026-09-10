package de.janitza.maven.gcs.impl

import java.nio.file.Path

import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import de.janitza.maven.gcs.api.config.{GCSConfig, ServiceAccountCredentials}
import de.janitza.maven.gcs.api.{Error, Success}
import de.janitza.maven.gcs.testsupport.GcsMockTransport.{DefaultAclEntity, allUsersReaderAclEntry, ownerAclEntry, serviceUnavailable}
import de.janitza.maven.gcs.testsupport.{GcsMockTransport, ProbeFileTypeDetector, TempFiles, TestKeys}
import org.apache.maven.plugin.logging.{Log, SystemStreamLog}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec

/**
  * Exercises the real HTTP edge. GCSConfig carries the HttpTransport, so a test can
  * put a recording transport in its place without touching production code and
  * without stubbing out the subject itself — everything from the object metadata to
  * the retry loop still runs.
  *
  * The content type in the request body comes from ProbeFileTypeDetector, registered
  * in the test classpath, so these assertions do not depend on the mime database of
  * the operating system either.
  */
class GoogleCloudStorageServiceSpec extends AnyFreeSpec with BeforeAndAfterAll {

  private val BucketName = "test-bucket"
  private val MaxAttempts = GoogleCloudStorageService.MAX_ATTEMPTS
  private val KnownExtension = ProbeFileTypeDetector.KnownExtension

  private var tempDir: Path = _

  override def beforeAll(): Unit = tempDir = TempFiles.directory("gcs-service-spec")

  override def afterAll(): Unit = TempFiles.deleteRecursively(tempDir)

  private def serviceAgainst(
    bucketDefaultAcl: Seq[String] = Seq(ownerAclEntry),
    insertResponses: Seq[MockLowLevelHttpResponse] = Seq.empty,
    log: Log = new SystemStreamLog
  ) = {
    val transport = new GcsMockTransport(BucketName, bucketDefaultAcl, insertResponses)
    val config = GCSConfig(
      transport,
      Seq("https://www.googleapis.com/auth/devstorage.full_control"),
      ServiceAccountCredentials("test@example.iam.gserviceaccount.com", TestKeys.rsaPrivateKey),
      JacksonFactory.getDefaultInstance,
      "gcs-maven-plugin-test",
      BucketName
    )
    (transport, new GoogleCloudStorageService(config, log))
  }

  private def uploadable(name: String): Path = TempFiles.fileAt(tempDir, name)

  "the object metadata sent to the API" - {

    "carries the storage path, the content type and the content disposition" in {
      val fileName = s"hello$KnownExtension"
      val (transport, service) = serviceAgainst()

      service.uploadFile(uploadable(fileName), Some("base"), sharePublic = false)

      val body = transport.initiationRequests.head.body
      assert(body.contains(s""""name":"base/$fileName""""))
      assert(body.contains(s""""contentType":"${ProbeFileTypeDetector.KnownMimeType}""""))
      assert(body.contains(s""""contentDisposition":"attachment; filename=\\"$fileName\\"""""))
    }

    "uses the bare file name when no bucket base path is given" in {
      val fileName = s"top$KnownExtension"
      val (transport, service) = serviceAgainst()

      service.uploadFile(uploadable(fileName), None, sharePublic = false)

      assert(transport.initiationRequests.head.body.contains(s""""name":"$fileName""""))
    }

    // An empty bucket-base-path reaches here as Some("") — GCSUploader passes
    // Option(m_BaseBucketPath), and an unset Maven parameter is the empty string, not null.
    // Without the guard in getStoragePath the object name would start with a slash and
    // create an unnamed first path segment in the bucket.
    "treats an empty bucket base path like none at all" in {
      val fileName = s"empty-prefix$KnownExtension"
      val (transport, service) = serviceAgainst()

      service.uploadFile(uploadable(fileName), Some(""), sharePublic = false)

      assert(transport.initiationRequests.head.body.contains(s""""name":"$fileName""""))
    }
  }

  "public sharing" - {

    "adds allUsers as a reader on top of the bucket default acl" in {
      val (transport, service) = serviceAgainst()

      service.uploadFile(uploadable(s"public$KnownExtension"), None, sharePublic = true)

      val body = transport.initiationRequests.head.body
      assert(body.contains(s""""entity":"$DefaultAclEntity""""))
      assert(body.contains(""""entity":"allUsers""""))
      assert(body.contains(""""role":"READER""""))
    }

    "does not add allUsers twice when the bucket default acl already grants public read" in {
      val (transport, service) = serviceAgainst(bucketDefaultAcl = Seq(allUsersReaderAclEntry, ownerAclEntry))

      service.uploadFile(uploadable(s"already-public$KnownExtension"), None, sharePublic = true)

      val body = transport.initiationRequests.head.body
      assert(body.split(""""entity":"allUsers"""", -1).length - 1 == 1)
    }

    "still grants public read when the bucket carries no default acl" in {
      val (transport, service) = serviceAgainst(bucketDefaultAcl = Seq.empty)

      service.uploadFile(uploadable(s"no-default$KnownExtension"), None, sharePublic = true)

      val body = transport.initiationRequests.head.body
      assert(body.contains(""""entity":"allUsers""""))
      assert(body.contains(""""role":"READER""""))
    }

    "sends no acl at all when sharing is off, so the bucket default applies" in {
      val (transport, service) = serviceAgainst()

      service.uploadFile(uploadable(s"private$KnownExtension"), None, sharePublic = false)

      assert(!transport.initiationRequests.head.body.contains("\"acl\""))
    }
  }

  "a file whose type cannot be determined" - {

    "is reported as an Error and never sent" in {
      val (transport, service) = serviceAgainst()

      val result = service.uploadFile(
        uploadable(s"broken${ProbeFileTypeDetector.FailingExtension}"), None, sharePublic = false)

      assert(result.isInstanceOf[Error])
      assert(transport.initiationRequests.isEmpty)
    }
  }

  "the retry loop" - {

    // Two assertions with two jobs. The request count pins the laziness of the LazyList
    // in insertWithRetry, and the Success guards against the opposite of the defect below:
    // turning every upload that only succeeded on a later attempt into a failed build.
    "keeps trying until the upload is accepted" in {
      val (transport, service) = serviceAgainst(insertResponses = Seq(serviceUnavailable, serviceUnavailable))

      val result = service.uploadFile(uploadable(s"retried$KnownExtension"), None, sharePublic = false)

      assert(transport.initiationRequests.size == 3)
      assert(result == Success(()))
    }

    "stops once the upload is accepted" in {
      val (transport, service) = serviceAgainst(insertResponses = Seq(serviceUnavailable))

      service.uploadFile(uploadable(s"once$KnownExtension"), None, sharePublic = false)

      assert(transport.initiationRequests.size == 2)
    }

    // This scenario used to be the characterization test for the most serious defect in
    // this plugin: a deploy in which not a single file arrived ended in BUILD SUCCESS,
    // and the log still claimed every file had been uploaded. Two faults compounded --
    // insertWithRetry answered Success exactly when every attempt had failed, and
    // uploadFile(Path, String, StorageObject) discarded the Result anyway.
    "reports an Error when every single attempt was rejected, and claims no upload" in {
      val log = new RecordingLog
      val (transport, service) =
        serviceAgainst(insertResponses = Seq.fill(MaxAttempts)(serviceUnavailable), log = log)

      val result = service.uploadFile(uploadable(s"doomed$KnownExtension"), None, sharePublic = false)

      assert(transport.initiationRequests.size == MaxAttempts)
      assert(result.isInstanceOf[Error])
      assert(log.messages.exists(_.startsWith("Uploading")))
      assert(!log.messages.exists(_.startsWith("Uploaded")))
    }
  }

  // The plugin used to leave contentType out of this request altogether: HttpUtil wrapped
  // the null from Files.probeContentType in a Success and setContentType(null) dropped the
  // field. With a fixed fallback the case is testable on every machine — .gcsunknown is
  // claimed by no detector on the test classpath, and neither supported platform sniffs
  // the content for it.
  "a file whose extension no detector knows" - {

    "is uploaded as application/octet-stream" in {
      val (transport, service) = serviceAgainst()

      service.uploadFile(uploadable(s"mystery${ProbeFileTypeDetector.UnknownExtension}"), None, sharePublic = false)

      val body = transport.initiationRequests.head.body
      withClue(body) {
        assert(body.contains(""""contentType":"application/octet-stream""""))
      }
    }
  }

  /** Keeps what the service logged, so a test can assert that no success was announced. */
  private class RecordingLog extends SystemStreamLog {
    private val recorded = scala.collection.mutable.Buffer.empty[String]

    def messages: Seq[String] = recorded.toSeq

    override def info(content: CharSequence): Unit = {
      recorded += content.toString
      super.info(content)
    }
  }

  // Not covered here: the plugin omits contentType entirely when probeContentType
  // answers null, because HttpUtil wraps that null in a Success and setContentType(null)
  // drops the field. The defect is real, but no assertion on it would travel.
  // What probeContentType answers for an unknown extension depends on which mime
  // database the machine happens to carry. Measured for ".gcsunknown": null on macOS
  // and null on cimg/openjdk:17.0, which ships no /usr/share/mime — but a Linux that
  // has shared-mime-info installed sniffs the content and answers text/plain for a
  // non-empty file. Building on whatever the host happens to know is what broke the
  // previous version of this suite.
}
