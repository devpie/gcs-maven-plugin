package de.janitza.maven.gcs

import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths}

import de.janitza.maven.gcs.api.{BuildException, IGCSConfig, IGoogleCloudStorageService}
import de.janitza.maven.gcs.impl.GoogleCloudStorageService
import de.janitza.maven.gcs.impl.config.GCSConfig
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
      val service = getGoogleCloudStorageService
      val pomFiles = new collection.mutable.ArrayBuffer[Path]
      Files.walkFileTree(
        Paths.get(m_FilesFilterBasePath),
        new FileFinder(m_FilesFilter, getLog, path => pomFiles += path)
      )
      for (path <- pomFiles) {
        uploadFile(service, path)
      }
    } catch {
      case e: BuildException => throw new MojoExecutionException("Config not buildable!", e)
      case e: IOException =>
        throw new MojoExecutionException("Scanning the files and uploading them resulted in an error!", e)
    }
  }

  @throws[BuildException]
  @throws[IOException]
  private def getGoogleCloudStorageService: IGoogleCloudStorageService =
    new GoogleCloudStorageService(getGCSConfig, getLog)

  @throws[BuildException]
  private def getGCSConfig: IGCSConfig =
    new GCSConfig.Builder()
      .setGCSApplicationName(m_ApplicationName)
      .setBucketName(m_BucketName)
      .setJsonSecretsFile(m_JsonSecretsFile)
      .build

  @throws[IOException]
  private def uploadFile(service: IGoogleCloudStorageService, path: Path) {
    service.uploadFile(path, Option(m_BaseBucketPath), m_SharePublic)
  }
}

