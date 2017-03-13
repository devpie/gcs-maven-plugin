package de.janitza.maven.gcs.impl

import java.io.IOException
import java.math.BigInteger
import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.FileContent
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.{Bucket, ObjectAccessControl, StorageObject}
import de.janitza.maven.gcs.api.config.GCSConfig
import de.janitza.maven.gcs.api.{Error, IGoogleCloudStorageService, Result, Success}
import de.janitza.maven.gcs.impl.util.{HttpUtil, StoragePath}
import org.apache.maven.plugin.logging.Log

import scala.collection.JavaConverters._


object GoogleCloudStorageService {
  val ROLE_READER = "READER"
  val USER_ALL_USERS = "allUsers"
  val PROJECTION = "full"
  val MAX_RETRY_ATTEMPTS = 10
}

class GoogleCloudStorageService @throws[IOException]
(val gcsConfig: GCSConfig, val log: Log)
  extends IGoogleCloudStorageService {

  import GoogleCloudStorageService._

  private val m_Client =
    new Storage.Builder(gcsConfig.httpTransport, gcsConfig.jsonFactory, authorize)
      .setApplicationName(gcsConfig.gcsApplicationName)
      .build
  private val m_Bucket =
    m_Client.buckets
      .get(gcsConfig.bucketName)
      .setProjection(PROJECTION)
      .execute

  logBucketInfo(gcsConfig.bucketName, m_Bucket)

  private def authorize: GoogleCredential = {
    val serviceAccountCredentials = gcsConfig.serviceAccountCredentials
    new GoogleCredential.Builder()
      .setTransport(gcsConfig.httpTransport)
      .setJsonFactory(gcsConfig.jsonFactory)
      .setServiceAccountScopes(gcsConfig.scopes.asJava)
      .setServiceAccountId(serviceAccountCredentials.accountId)
      .setServiceAccountPrivateKey(serviceAccountCredentials.privateKey)
      .build
  }

  override def uploadFile(file: Path, relativePathInStorage: Option[String], sharePublic: Boolean): Result[Unit] = {
    val storagePath = getStoragePath(relativePathInStorage, file.getFileName.toString)
    val storageObjectResult = createStorageObject(storagePath, file, sharePublic, m_Bucket)
    storageObjectResult match {
      case Success(storageObject) => Success(uploadFile(file, storagePath, storageObject))
      case e: Error => e
    }
  }

  def uploadFile(file: Path, storagePath: String, storageObject: StorageObject): Unit = {
    logFileUploading(file, storagePath)
    val t1 = Instant.now
    insertWithRetry(file, storageObject, MAX_RETRY_ATTEMPTS)
    logFileUploaded(file, storagePath, t1)
  }

  @throws[IOException]
  private def createInsert(file: Path, storageObject: StorageObject): Storage#Objects#Insert = {
    val fileSize: Long = Files.size(file)
    val insertion: Storage#Objects#Insert =
      m_Client.objects.insert(
        m_Bucket.getName,
        storageObject,
        new FileContent(storageObject.getContentType, file.toFile)
      )
    val mediaHttpUploaderInsertion = insertion.getMediaHttpUploader
    if (mediaHttpUploaderInsertion != null) {
      mediaHttpUploaderInsertion.setDirectUploadEnabled(false)
      mediaHttpUploaderInsertion.setProgressListener(new GCSProgressListener(fileSize, log))
    }
    insertion
  }

  private def insertWithRetry(file: Path, storageObject: StorageObject, maxRetryCount: Int): Result[Unit] = {
    val errorResults = Stream.from(1).take(maxRetryCount)
      .map(Insertion(file, storageObject).execute)
      .takeWhile(result => result.isInstanceOf[Error]).toSeq
    if (errorResults.nonEmpty && errorResults.size < maxRetryCount) {
      errorResults.last
    } else {
      Success()
    }
  }

  private case class Insertion(file: Path, storageObject: StorageObject) {
    def execute(counter: Int): Result[Unit] = {
      try {
        createInsert(file, storageObject).execute
        Success()
      } catch {
        case e: IOException => {
          val errorMessage: String = "Upload was interrupted."
          log.info(errorMessage)
          Error(errorMessage, Some(e))
        }
      }
    }
  }

  private def logBucketInfo(bucketName: String, bucket: Bucket) {
    log.info(s"Bucket name: $bucketName")
    log.info(s"Bucket location: ${bucket.getLocation}")
  }

  private def logFileUploaded(file: Path, storagePath: String, t1: Instant) {
    log.info(s"Uploaded $file to $storagePath in ${Duration.between(t1, Instant.now)}")
  }

  private def logFileUploading(file: Path, storagePath: String) {
    log.info(s"Uploading $file to $storagePath")
  }

  private def getStoragePath(relativePathInStorage: Option[String], fileName: String) =
    relativePathInStorage.map({
      case rp: String if rp.isEmpty => fileName
      case rp: String => StoragePath.join(rp, fileName)
    }) getOrElse fileName

  private def createStorageObject(storagePath: String, file: Path, sharePublic: Boolean, bucket: Bucket): Result[StorageObject] = {
    val fileName = file.getFileName.toString
    HttpUtil.getMimeType(file) match {
      case Success(mimeType) => size(file) match {
        case Success(fileSize) =>
          Success(createStorageObject(storagePath, fileName, fileSize, sharePublic, bucket, mimeType))
        case Error(message, exception) => Error(message, exception)
      }
      case Error(message, exception) => Error(message, exception)
    }
  }

  private def createStorageObject(storagePath: String, fileName: String, fileSize: BigInteger, sharePublic: Boolean, bucket: Bucket, mimeType: String): StorageObject = {
    val storageObject = new StorageObject()
      .setName(storagePath)
      .setContentDisposition(HttpUtil.getContentDisposition(fileName))
      .setContentType(mimeType)
      .setSize(fileSize)
    if (sharePublic) {
      storageObject.setAcl(addPublicReadAccess(getDefaultObjectAcl(bucket)).asJava)
    }
    storageObject
  }

  @throws[IOException]
  private def size(file: Path): Result[BigInteger] = {
    try {
      Success(BigInteger.valueOf(Files.size(file)))
    } catch {
      case e: IOException => Error("Couldn't determine size of file to be uploaded!", Some(e))
    }
  }

  private def addPublicReadAccess(defaultAcl: Seq[ObjectAccessControl]): Seq[ObjectAccessControl] = {
    val alreadyShared = defaultAcl.toStream.map(_.getEntity).exists(USER_ALL_USERS.equals(_))
    if (alreadyShared)
      defaultAcl
    else
      defaultAcl :+
        new ObjectAccessControl()
          .setEntity(USER_ALL_USERS)
          .setRole(ROLE_READER)
  }

  private def getDefaultObjectAcl(bucket: Bucket) =
    Option(bucket.getDefaultObjectAcl).map {
      _.asScala.toList
    } getOrElse Seq()
}

