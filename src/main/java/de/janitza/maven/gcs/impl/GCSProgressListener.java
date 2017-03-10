package de.janitza.maven.gcs.impl;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 26.02.15
 * Time: 10:19
 */
public class GCSProgressListener implements MediaHttpUploaderProgressListener {

    private final long m_FileSize;
    private final Log m_Log;

    public GCSProgressListener(
            final long fileSize,
            final Log log
    ) {
        m_FileSize = fileSize;
        m_Log = log;
    }

    @Override
    public void progressChanged(final MediaHttpUploader uploader) throws IOException {
        final long percentage = Math.round(Math.floor(uploader.getProgress() * 100));
        final long numBytesUploaded = uploader.getNumBytesUploaded() / 1024 / 1024;
        final long fileSizeInMB = m_FileSize / 1024 / 1024;
        switch (uploader.getUploadState()) {
            case INITIATION_COMPLETE:
                m_Log.info(
                        String.format(
                                "%s ::: Initiation completed.",
                                LocalDateTime.now()));
                break;
            case INITIATION_STARTED:
                m_Log.info(
                        String.format(
                                "%s ::: Initiation Started.",
                                LocalDateTime.now()));
                break;
            case MEDIA_COMPLETE:
                m_Log.info(
                        String.format(
                                "%s ::: Uploaded %s MB of %s MB ::: %s%%",
                                LocalDateTime.now(),
                                numBytesUploaded,
                                fileSizeInMB,
                                percentage));
                break;
            case MEDIA_IN_PROGRESS:
                m_Log.info(
                        String.format(
                                "%s ::: Uploaded %s MB of %s MB ::: %s%%",
                                LocalDateTime.now(),
                                numBytesUploaded,
                                fileSizeInMB,
                                percentage));
                break;
            case NOT_STARTED:
                m_Log.info(
                        String.format(
                                "%s ::: Initiation not started yet. Uploaded %s MB of %s MB ::: %s%%",
                                LocalDateTime.now(),
                                numBytesUploaded,
                                fileSizeInMB,
                                percentage));
                break;
        }
    }
}