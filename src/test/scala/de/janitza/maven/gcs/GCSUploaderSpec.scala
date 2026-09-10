package de.janitza.maven.gcs

import java.io.File
import java.nio.file.{Path, Paths}

import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import de.janitza.maven.gcs.api.config.{GCSConfig, ServiceAccountCredentials}
import de.janitza.maven.gcs.impl.GoogleCloudStorageService
import de.janitza.maven.gcs.testsupport.GcsMockTransport.{ownerAclEntry, serviceUnavailable}
import de.janitza.maven.gcs.testsupport.{GcsMockTransport, ProbeFileTypeDetector, TempFiles, TestKeys}
import org.apache.maven.plugin.logging.SystemStreamLog
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import scala.compiletime.uninitialized

/**
  * Covers the two decisions the mojo makes around the upload itself: which directory it
  * scans, and what happens to the remaining files once one of them fails.
  *
  * The mojo builds its own storage service from a secrets file, so execute() is out of
  * reach here. Both decisions live in methods that take what they need as an argument,
  * which is what makes them testable against the recording transport.
  */
class GCSUploaderSpec extends AnyFreeSpec with BeforeAndAfterAll {

  private val BucketName = "test-bucket"
  private val MaxAttempts = GoogleCloudStorageService.MAX_ATTEMPTS
  private val KnownExtension = ProbeFileTypeDetector.KnownExtension

  private var tempDir: Path = uninitialized

  override def beforeAll(): Unit = tempDir = TempFiles.directory("gcs-uploader-spec")

  override def afterAll(): Unit = TempFiles.deleteRecursively(tempDir)

  private def serviceAgainst(insertResponses: Seq[MockLowLevelHttpResponse]) = {
    val transport = new GcsMockTransport(BucketName, Seq(ownerAclEntry), insertResponses)
    val config = GCSConfig(
      transport,
      Seq("https://www.googleapis.com/auth/devstorage.full_control"),
      ServiceAccountCredentials("test@example.iam.gserviceaccount.com", TestKeys.rsaPrivateKey),
      JacksonFactory.getDefaultInstance,
      "gcs-maven-plugin-test",
      BucketName
    )
    (transport, new GoogleCloudStorageService(config, new SystemStreamLog))
  }

  /** Maven injects the mojo parameters into the fields, so a test sets them the same way. */
  private def uploaderWith(baseDir: File, filesFilterBasePath: String): GCSUploader = {
    val uploader = new GCSUploader
    setParameter(uploader, "m_BaseDir", baseDir)
    setParameter(uploader, "m_FilesFilterBasePath", filesFilterBasePath)
    uploader
  }

  private def setParameter(uploader: GCSUploader, fieldName: String, value: AnyRef): Unit = {
    val field = classOf[GCSUploader].getDeclaredField(fieldName)
    field.setAccessible(true)
    field.set(uploader, value)
  }

  "a file that cannot be uploaded" - {

    // Fail-fast: the upload stops at the first rejected file. Whoever replaces the
    // LazyList by a strict collection turns that into "upload everything, report the
    // first failure afterwards" and this test says so.
    "ends the run, so no later file is even attempted" in {
      val (transport, service) = serviceAgainst(Seq.fill(MaxAttempts)(serviceUnavailable))
      val rejected = TempFiles.fileAt(tempDir, s"rejected$KnownExtension")
      val wouldHaveWorked = TempFiles.fileAt(tempDir, s"would-have-worked$KnownExtension")

      val failure = new GCSUploader().uploadUntilFirstError(service, Seq(rejected, wouldHaveWorked))

      assert(failure.isDefined)
      assert(transport.initiationRequests.size == MaxAttempts)
      assert(transport.initiationRequests.forall(_.body.contains(rejected.getFileName.toString)))
    }
  }

  "the directory the files filter is applied to" - {

    "is files-filter-base-path when it is set" in {
      val uploader = uploaderWith(baseDir = new File("/base/directory"), filesFilterBasePath = "/filter/base/path")

      assert(uploader.filesFilterBasePath == Paths.get("/filter/base/path"))
    }

    // Maven leaves an unset parameter at null; a parameter whose default expression
    // resolves to nothing arrives as the empty string. Paths.get would answer the working
    // directory for the latter and throw a NullPointerException for the former.
    "falls back to base-directory when files-filter-base-path is unset" in {
      val uploader = uploaderWith(baseDir = new File("/base/directory"), filesFilterBasePath = null)

      assert(uploader.filesFilterBasePath == Paths.get("/base/directory"))
    }

    "falls back to base-directory when files-filter-base-path arrives empty" in {
      val uploader = uploaderWith(baseDir = new File("/base/directory"), filesFilterBasePath = "")

      assert(uploader.filesFilterBasePath == Paths.get("/base/directory"))
    }
  }
}
