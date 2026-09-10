package de.janitza.maven.gcs

import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths}

import de.janitza.maven.gcs.api.{Error, IGoogleCloudStorageService, Result, Success}
import de.janitza.maven.gcs.impl.GoogleCloudStorageService
import de.janitza.maven.gcs.impl.config.GCSConfigBuilder
import org.apache.maven.plugin.{AbstractMojo, MojoExecutionException}
import org.apache.maven.plugins.annotations.{LifecyclePhase, Mojo, Parameter}

import scala.util.control.NonFatal
import scala.compiletime.uninitialized

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
  private var m_BaseDir: File = uninitialized

  /**
    * Location of the file.
    */
  @Parameter(
    defaultValue = "${gcs-application-name}",
    property = "gcs-application-name",
    required = true,
    alias = "gcs-application-name"
  )
  private var m_ApplicationName: String = uninitialized

  /**
    * A filter expression identifying the files to be uploaded.
    */
  @Parameter(
    defaultValue = "${files-filter}",
    property = "files-filter",
    required = true,
    alias = "files-filter"
  )
  private var m_FilesFilter: String = uninitialized

  /**
    * A GCS bucket uri for uploading the files to.
    */
  @Parameter(
    defaultValue = "${bucket-name}",
    property = "bucket-name",
    required = true,
    alias = "bucket-name"
  )
  private var m_BucketName: String = uninitialized

  /**
    * The GCS secrets file.
    */
  @Parameter(
    defaultValue = "${project.basedir}/src/main/gcs/secret.json",
    property = "json-secrets-file",
    required = true,
    alias = "json-secrets-file"
  )
  private var m_JsonSecretsFile: String = uninitialized

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
  private var m_BaseBucketPath: String = uninitialized

  /**
    * The root path for recursively applying the files filter to.
    */
  @Parameter(
    defaultValue = "${files-filter-base-path}",
    property = "files-filter-base-path",
    required = false,
    alias = "files-filter-base-path"
  )
  private var m_FilesFilterBasePath: String = uninitialized

  @throws[MojoExecutionException]
  def execute(): Unit = {
    val failure: Option[Error] =
      try {
        val filesToUpload = findFilesToUpload
        getGoogleCloudStorageService match {
          case Success(service) => uploadUntilFirstError(service, filesToUpload)
          case e: Error => Some(e)
        }
      } catch {
        // An exception carrying its own message keeps it; only an unforeseen one gets the
        // generic wording.
        case e: MojoExecutionException => throw e
        case NonFatal(e) =>
          throw new MojoExecutionException("Scanning the files and uploading them resulted in an error!", e)
      }
    failure.foreach(failTheBuild)
  }

  private def findFilesToUpload: Seq[Path] = {
    val foundFiles = new collection.mutable.ArrayBuffer[Path]
    val root = filesFilterBasePath
    Files.walkFileTree(root, new FileFinder(root, m_FilesFilter, getLog, path => foundFiles += path))
    foundFiles.toSeq
  }

  /**
    * The directory the files filter is applied to. files-filter-base-path is optional, so
    * base-directory is what the plugin falls back to. Maven hands an unset parameter over
    * as null or as the empty string, depending on how it was declared.
    */
  private[gcs] def filesFilterBasePath: Path =
    Option(m_FilesFilterBasePath).filter(_.trim.nonEmpty).map(Paths.get(_))
      .orElse(Option(m_BaseDir).map(_.toPath))
      .getOrElse(throw new MojoExecutionException(
        "Neither files-filter-base-path nor base-directory is set, so there is no directory to scan."))

  /**
    * Uploads the files and stops at the first one that fails, so a deploy that is going
    * wrong does not keep pushing further files into the bucket. The LazyList is what makes
    * it stop: a strict map would upload every file before collectFirst ever looks.
    */
  private[gcs] def uploadUntilFirstError(service: IGoogleCloudStorageService, files: Seq[Path]): Option[Error] =
    files.to(LazyList).map(uploadFile(service, _)).collectFirst({ case e: Error => e })

  private def failTheBuild(error: Error): Nothing = error match {
    case Error(message, Some(exception)) => throw new MojoExecutionException(message, exception)
    case Error(message, None) => throw new MojoExecutionException(message)
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
