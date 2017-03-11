package de.janitza.maven.gcs.impl

import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener
import org.apache.maven.plugin.logging.Log
import java.io.IOException
import java.time.LocalDateTime

import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState

class GCSProgressListener(val m_FileSize: Long, val m_Log: Log) extends MediaHttpUploaderProgressListener {
  @throws[IOException]
  def progressChanged(uploader: MediaHttpUploader) {
    val percentage = Math.floor(uploader.getProgress * 100).round
    val numBytesUploaded = uploader.getNumBytesUploaded / 1024 / 1024
    val fileSizeInMB = m_FileSize / 1024 / 1024
    val now = LocalDateTime.now
    uploader.getUploadState match {
      case UploadState.INITIATION_COMPLETE =>
        m_Log.info(s"$now ::: Initiation completed.")
      case UploadState.INITIATION_STARTED =>
        m_Log.info(s"$now ::: Initiation Started.")
      case UploadState.MEDIA_COMPLETE =>
        m_Log.info(s"$now ::: Uploaded $numBytesUploaded MB of $fileSizeInMB MB ::: $percentage%")
      case UploadState.MEDIA_IN_PROGRESS =>
        m_Log.info(s"$now ::: Uploaded $numBytesUploaded MB of $fileSizeInMB MB ::: $percentage%")
      case UploadState.NOT_STARTED =>
        m_Log.info(
          s"$now ::: Initiation not started yet. Uploaded $numBytesUploaded MB of $fileSizeInMB MB ::: $percentage%")
    }
  }
}