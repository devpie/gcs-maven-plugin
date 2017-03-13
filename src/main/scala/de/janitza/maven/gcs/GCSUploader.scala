package de.janitza.maven.gcs

import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths}

import de.janitza.maven.gcs.api.{Error, IGoogleCloudStorageService, Result, Success}
import de.janitza.maven.gcs.impl.GoogleCloudStorageService
import de.janitza.maven.gcs.impl.config.GCSConfigBuilder
import org.apache.maven.plugin.{AbstractMojo, MojoExecutionException}
import org.apache.maven.plugins.annotations.{LifecyclePhase, Mojo, Parameter}

/**
  * Goal which uploads files into a specified bucket of the Google Cloud Storage
  */
@Mojo(name = "upload", defaultPhase = LifecyclePhase.DEPLOY)
class GCSUploader extends AbstractMojo {

  /**
    * Location of the build directory.
    */
  @Parameter(
    defaultValue = "${project.basedir}",
    property = "base-directory",
    required = false,
    alias = "base-directory"
  )
  private var m_BaseDir: File = _

  /**
    * Location of the file.
    */
  @Parameter(
    defaultValue = "${gcs-application-name}",
    property = "gcs-application-name",
    required = true,
    alias = "gcs-application-name"
  )
  private var m_ApplicationName: String = _

  /**
    * A filter expression identifying the files to be uploaded.
    */
  @Parameter(
    defaultValue = "${files-filter}",
    property = "files-filter",
    required = true,
    alias = "files-filter"
  )
  private var m_FilesFilter: String = _

  /**
    * A GCS bucket uri for uploading the files to.
    */
  @Parameter(
    defaultValue = "${bucket-name}",
    property = "bucket-name",
    required = true,
    alias = "bucket-name"
  )
  private var m_BucketName: String = _

  /**
    * The GCS secrets file.
    */
  @Parameter(
    defaultValue = "${project.basedir}/src/main/gcs/secret.json",
    property = "json-secrets-file",
    required = true,
    alias = "json-secrets-file"
  )
  private var m_JsonSecretsFile: String = _

  /**
    * If the files should be shared publicly.
    */
  @Parameter(
    defaultValue = "${share-public}",
    property = "share-public",
    required = true,
    alias = "share-public"
  )
  private var m_SharePublic: Boolean = false

  /**
    * The relative base path within the google bucket.
    */
  @Parameter(
    defaultValue = "${bucket-base-path}",
    property = "bucket-base-path",
    required = false,
    alias = "bucket-base-path"
  )
  private var m_BaseBucketPath: String = _

  /**
    * The root path for recursively applying the files filter to.
    */
  @Parameter(
    defaultValue = "${files-filter-base-path}",
    property = "files-filter-base-path",
    required = false,
    alias = "files-filter-base-path"
  )
  private var m_FilesFilterBasePath: String = _

  @throws[MojoExecutionException]
  def execute() {
    try {
      val pomFiles = new collection.mutable.ArrayBuffer[Path]
      Files.walkFileTree(
        Paths.get(m_FilesFilterBasePath),
        new FileFinder(m_FilesFilter, getLog, path => pomFiles += path)
      )

      val result: Option[Result[Unit]] = getGoogleCloudStorageService match {
        case Success(service) =>
          pomFiles.toStream.map(uploadFile(service, _)).collectFirst({ case e: Error => e })
        case e: Error => Some(e)
      }

      result match {
        case Some(Error(message, Some(exception))) => throw new MojoExecutionException(message, exception)
        case Some(Error(message, None)) => throw new MojoExecutionException(message)
        case Some(Success(_)) => {}
        case None => {}
      }
    } catch {
      case e: Any =>
        throw new MojoExecutionException("Scanning the files and uploading them resulted in an error!", e)
    }
  }

  @throws[IOException]
  private def getGoogleCloudStorageService: Result[IGoogleCloudStorageService] =
    getGCSConfig match {
      case Success(gcsConfig) => Success(new GoogleCloudStorageService(gcsConfig, getLog))
      case e: Error => e
    }


  private def getGCSConfig = GCSConfigBuilder(m_JsonSecretsFile, m_ApplicationName, m_BucketName).build

  private def uploadFile(service: IGoogleCloudStorageService, path: Path): Result[Unit] =
    service.uploadFile(path, Option(m_BaseBucketPath), m_SharePublic)

}

