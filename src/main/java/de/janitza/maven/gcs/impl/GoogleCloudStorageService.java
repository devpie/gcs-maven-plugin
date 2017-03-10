package de.janitza.maven.gcs.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.ObjectAccessControl;
import com.google.api.services.storage.model.StorageObject;
import de.janitza.maven.gcs.api.IGoogleCloudStorageService;
import de.janitza.maven.gcs.api.config.IGCSConfig;
import de.janitza.maven.gcs.api.config.loader.IServiceAccountCredentials;
import de.janitza.maven.gcs.impl.utils.HttpUtil;
import de.janitza.maven.gcs.impl.utils.Lists;
import de.janitza.maven.gcs.impl.utils.StoragePath;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 11:48
 */
public class GoogleCloudStorageService implements IGoogleCloudStorageService {


    public static final String ROLE_READER = "READER";
    public static final String USER_ALL_USERS = "allUsers";

    private final IGCSConfig m_IGCSConfig;
    private final GoogleCredential m_Credentials;
    private final Storage m_Client;
    private final Bucket m_Bucket;
    private final Log m_Log;

    public GoogleCloudStorageService(
            final IGCSConfig igcsConfig,
            final Log log
    ) throws IOException {
        m_Log = log;
        m_IGCSConfig = igcsConfig;
        m_Credentials = authorize();
        m_Client = new Storage.Builder(
                m_IGCSConfig.getHttpTransport(),
                m_IGCSConfig.getJsonFactory(),
                m_Credentials
        ).setApplicationName(
                m_IGCSConfig.getGCSApplicationName()
        ).build();
        m_Bucket = m_Client.buckets()
                .get(m_IGCSConfig.getBucketName())
                .setProjection("full")
                .execute();
        logBucketInfo(m_IGCSConfig.getBucketName(), m_Bucket);
    }

    private GoogleCredential authorize() {
        final IServiceAccountCredentials serviceAccountCredentials =
                m_IGCSConfig.getServiceAccountCredentials();

        return new GoogleCredential.Builder()
                .setTransport(m_IGCSConfig.getHttpTransport())
                .setJsonFactory(m_IGCSConfig.getJsonFactory())
                .setServiceAccountScopes(m_IGCSConfig.getScopes())
                .setServiceAccountId(serviceAccountCredentials.getAccountId())
                .setServiceAccountPrivateKey(serviceAccountCredentials.getPrivateKey())
                .build();
    }

    @Override
    public void uploadFile(
            final Path file,
            final Optional<String> relativePathInStorage,
            boolean sharePublic
    ) throws IOException {

        final String fileName = file.getFileName().toString();
        final String storagePath = getStoragePath(relativePathInStorage, fileName);

        final StorageObject object = createStorageObject(
                storagePath,
                file,
                sharePublic,
                m_Bucket
        );

        logFileUploading(file, storagePath);

        final Instant t1 = Instant.now();

        retry(file, object, 10);

        logFileUploaded(file, storagePath, t1);
    }

    private Storage.Objects.Insert createInsert(
            final Path file,
            final StorageObject object
    ) throws IOException {
        final long fileSize = Files.size(file);
        final Storage.Objects.Insert insertion = m_Client.objects().insert(
                m_Bucket.getName(),
                object,
                new FileContent(object.getContentType(), file.toFile())
        );

        final MediaHttpUploader mediaHttpUploaderInsertion = insertion
                .getMediaHttpUploader();
        if (mediaHttpUploaderInsertion != null) {
            mediaHttpUploaderInsertion.setDirectUploadEnabled(false);
            mediaHttpUploaderInsertion
                    .setProgressListener(new GCSProgressListener(fileSize, m_Log));
        }
        return insertion;
    }

    private void retry(
            final Path file,
            final StorageObject object,
            final int maxRetryCount
    ) throws IOException {
        final IOException exception = new IOException("Retry failed!");
        int i = 0;
        while (i < maxRetryCount) {
            try {
                createInsert(file, object).execute();
                break;
            } catch (IOException e) {
                exception.addSuppressed(e);
                i++;
                if (i < maxRetryCount) {
                    m_Log.info(
                            "Upload was interrupted. Retrying the upload! " +
                                    "Resuming is currently not supported by the" +
                                    " google lib!"
                    );
                }
            }
        }
        if (i == maxRetryCount) {
            throw exception;
        }
    }

    private void logBucketInfo(
            final String bucketName,
            final Bucket bucket
    ) {
        m_Log.info("Bucket name: " + bucketName);
        m_Log.info("Bucket location: " + bucket.getLocation());
    }

    private void logFileUploaded(
            final Path file,
            final String storagePath,
            final Instant t1
    ) {
        m_Log.info(
                String.format(
                        "Uploaded %s to %s in %s",
                        file,
                        storagePath,
                        Duration.between(t1, Instant.now())
                )
        );
    }

    private void logFileUploading(
            final Path file,
            final String storagePath
    ) {
        m_Log.info(
                String.format(
                        "Uploading %s to %s",
                        file,
                        storagePath
                )
        );
    }

    private String getStoragePath(
            final Optional<String> relativePathInStorage,
            final String fileName
    ) {
        return relativePathInStorage.map(
                (rp) -> rp.isEmpty() ? fileName : StoragePath.join(rp, fileName)
        ).orElse(fileName);
    }

    private StorageObject createStorageObject(
            final String storagePath,
            final Path file,
            final boolean sharePublic,
            final Bucket bucket
    ) throws IOException {
        final String fileName = file.getFileName().toString();
        StorageObject object =
                new StorageObject()
                        .setName(storagePath)
                        .setContentDisposition(
                                HttpUtil.getContentDisposition(fileName))
                        .setContentType(HttpUtil.getMimeType(file))
                        .setSize(size(file));

        if (sharePublic) {
            object.setAcl(addPublicReadAccess(getDefaultObjectAcl(bucket)));
        }

        return object;
    }

    private BigInteger size(final Path file) throws IOException {
        return BigInteger.valueOf(Files.size(file));
    }

    private List<ObjectAccessControl> addPublicReadAccess(
            final List<ObjectAccessControl> defaultAcl
    ) {
        final List<ObjectAccessControl> acl = new ArrayList<>(defaultAcl);

        final boolean alreadyShared = !acl.stream().anyMatch((oac) -> {
            return USER_ALL_USERS.equals(oac.getEntity());
        });

        if (alreadyShared) {
            acl.add(
                    new ObjectAccessControl()
                            .setEntity(USER_ALL_USERS)
                            .setRole(ROLE_READER)
            );
        }

        return acl;
    }

    private List<ObjectAccessControl> getDefaultObjectAcl(final Bucket bucket) {
        return Lists.ensureAList(bucket.getDefaultObjectAcl());
    }
}
