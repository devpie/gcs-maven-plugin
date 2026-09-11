package de.janitza.maven.gcs

import java.io.File
import java.nio.file.{Path, Paths}

import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import de.janitza.maven.gcs.api.config.{GCSConfig, ServiceAccountCredentials}
import de.janitza.maven.gcs.api.{Error, Success}
import de.janitza.maven.gcs.impl.GoogleCloudStorageService
import de.janitza.maven.gcs.testsupport.GcsMockTransport.{ownerAclEntry, serviceUnavailable}
import de.janitza.maven.gcs.testsupport.{GcsMockTransport, Permissions, ProbeFileTypeDetector, TempFiles, TestKeys}
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.SystemStreamLog
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import scala.compiletime.uninitialized

/**
  * Covers the decisions the mojo makes around the upload itself: which directory it scans,
  * what it does when the scan cannot read part of that directory, and what happens to the
  * remaining files once an upload fails.
  *
  * The mojo builds its own storage service from a secrets file, so execute() is only
  * reachable for a run that ends before the service is built. The other decisions live in
  * methods that take what they need as an argument, which is what makes them testable
  * against the recording transport.
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
  private def uploaderWith(baseDir: File, filesFilterBasePath: String, filesFilter: String = "*.jar"): GCSUploader = {
    val uploader = new GCSUploader
    setParameter(uploader, "m_BaseDir", baseDir)
    setParameter(uploader, "m_FilesFilterBasePath", filesFilterBasePath)
    setParameter(uploader, "m_FilesFilter", filesFilter)
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

  "a directory the scan cannot read" - {

    // The scan silently dropped whatever sat below such a directory, so the plugin
    // uploaded an incomplete set of files and still reported the deploy as successful.
    "ends the run before a single file is uploaded" in {
      withUnreadableDirectoryBelow { (scanRoot, unreadableDirectory) =>
        val uploader = uploaderWith(baseDir = scanRoot.toFile, filesFilterBasePath = null)

        val result = uploader.findFilesToUpload

        assert(result == Error(
          s"The scan could not read 1 entry, so the files to upload are unknown: $unreadableDirectory"))
      }
    }

    // A tree can go wrong in many places at once. Naming every single one would bury the
    // number of them, and that number is what tells the user how big the problem is.
    "is counted in full, but only the first ten are named" in {
      val scanRoot = TempFiles.directory("gcs-uploader-spec-many-unreadable")
      try {
        val unreadableDirectories =
          (0 until 12).map(index => TempFiles.fileAt(scanRoot, f"secret-$index%02d/hidden.jar").getParent)

        Permissions.withoutAnyAccessTo(unreadableDirectories) {
          val uploader = uploaderWith(baseDir = scanRoot.toFile, filesFilterBasePath = null)

          val Error(message, _) = uploader.findFilesToUpload: @unchecked

          assert(message.startsWith("The scan could not read 12 entries,"))
          assert(message.endsWith(", and 2 more"))
          assert(unreadableDirectories.count(directory => message.contains(directory.toString)) == 10)
        }
      } finally TempFiles.deleteRecursively(scanRoot)
    }

    "names the directory in the message that fails the build" in {
      withUnreadableDirectoryBelow { (scanRoot, unreadableDirectory) =>
        val uploader = uploaderWith(baseDir = scanRoot.toFile, filesFilterBasePath = null)

        val failure = intercept[MojoExecutionException](uploader.execute())

        assert(failure.getMessage.contains(unreadableDirectory.toString))
      }
    }
  }

  /**
    * Builds a tree of its own for each test, because the unreadable directory has to be
    * deleted again and the suite shares tempDir across all tests.
    */
  private def withUnreadableDirectoryBelow(body: (Path, Path) => Unit): Unit = {
    val scanRoot = TempFiles.directory("gcs-uploader-spec-unreadable")
    try {
      val unreadableDirectory = TempFiles.fileAt(scanRoot, "secret/hidden.jar").getParent
      Permissions.withoutAnyAccessTo(unreadableDirectory)(body(scanRoot, unreadableDirectory))
    } finally TempFiles.deleteRecursively(scanRoot)
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
