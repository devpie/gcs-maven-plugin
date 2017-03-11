package de.janitza.maven.gcs.impl

import java.io.IOException
import java.math.BigInteger
import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.FileContent
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.{Bucket, ObjectAccessControl, StorageObject}
import de.janitza.maven.gcs.api.{IGCSConfig, IGoogleCloudStorageService}
import de.janitza.maven.gcs.impl.util.{HttpUtil, StoragePath}
import org.apache.maven.plugin.logging.Log

import scala.collection.JavaConverters._


object GoogleCloudStorageService {
  val ROLE_READER = "READER"
  val USER_ALL_USERS = "allUsers"
  val PROJECTION = "full"
}

class GoogleCloudStorageService @throws[IOException]
(val gcsConfig: IGCSConfig, val log: Log)
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

  @throws[IOException]
  override def uploadFile(file: Path, relativePathInStorage: Option[String], sharePublic: Boolean) {
    val fileName = file.getFileName.toString
    val storagePath = getStoragePath(relativePathInStorage, fileName)
    val storageObject: StorageObject = createStorageObject(storagePath, file, sharePublic, m_Bucket)
    logFileUploading(file, storagePath)
    val t1: Instant = Instant.now
    retry(file, storageObject, 10)
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

  @throws[IOException]
  private def retry(file: Path, storageObject: StorageObject, maxRetryCount: Int) {
    val exception: IOException = new IOException("Retry failed!")
    var i = 0
    var done = false
    while (!done && i < maxRetryCount)
      try {
        createInsert(file, storageObject).execute
        done = true
      } catch {
        case e: IOException => {
          exception.addSuppressed(e)
          i += 1
          if (i < maxRetryCount)
            log.info(
              "Upload was interrupted. Retrying the upload! Resuming is currently not supported by the google lib!")
          else
            done = true
        }
      }
    if (i >= maxRetryCount) throw exception
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

  @throws[IOException]
  private def createStorageObject(storagePath: String, file: Path, sharePublic: Boolean, bucket: Bucket) = {
    val fileName: String = file.getFileName.toString
    val storageObject = new StorageObject()
      .setName(storagePath)
      .setContentDisposition(HttpUtil.getContentDisposition(fileName))
      .setContentType(HttpUtil.getMimeType(file))
      .setSize(size(file))
    if (sharePublic) {
      storageObject.setAcl(addPublicReadAccess(getDefaultObjectAcl(bucket)).asJava)
    }
    storageObject
  }

  @throws[IOException]
  private def size(file: Path): BigInteger = BigInteger.valueOf(Files.size(file))

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

