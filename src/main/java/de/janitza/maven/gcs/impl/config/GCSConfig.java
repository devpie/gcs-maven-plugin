package de.janitza.maven.gcs.impl.config;

import com.google.api.client.googleapis.GoogleUtils;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.StorageScopes;
import de.janitza.maven.gcs.api.config.IGCSConfig;
import de.janitza.maven.gcs.api.config.loader.IServiceAccountCredentials;
import de.janitza.maven.gcs.api.exception.BuildException;
import de.janitza.maven.gcs.impl.config.loader.JsonCredentialsLoader;
import de.janitza.maven.gcs.impl.utils.Strings;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 12:27
 */
public class GCSConfig implements IGCSConfig {

    private final HttpTransport m_HttpTransport;
    private final Set<String> m_Scopes;
    private final IServiceAccountCredentials m_ServiceAccountCredentials;
    private final JsonFactory m_JsonFactory;
    private final String m_GCSApplicationName;
    private final String m_BucketName;

    private GCSConfig(
            final HttpTransport httpTransport,
            final Set<String> scopes,
            final IServiceAccountCredentials serviceAccountCredentials,
            final JsonFactory jsonFactory,
            final String gcsApplicationName,
            final String bucketName
    ) {
        m_Scopes = scopes;
        m_HttpTransport = httpTransport;
        m_ServiceAccountCredentials = serviceAccountCredentials;
        m_JsonFactory = jsonFactory;
        m_GCSApplicationName = gcsApplicationName;
        m_BucketName = bucketName;
    }

    @Override
    public Collection<String> getScopes() {
        return m_Scopes;
    }

    @Override
    public IServiceAccountCredentials getServiceAccountCredentials() {
        return m_ServiceAccountCredentials;
    }

    @Override
    public String getGCSApplicationName() {
        return m_GCSApplicationName;
    }

    @Override
    public String getBucketName() {
        return m_BucketName;
    }

    @Override
    public JsonFactory getJsonFactory() {
        return m_JsonFactory;
    }

    @Override
    public HttpTransport getHttpTransport() {
        return m_HttpTransport;
    }


    public static class Builder {
        private String m_JsonSecretsFile;
        private String m_GCSApplicationName;
        private String m_BucketName;

        public GCSConfig build() throws BuildException {
            final JsonFactory jsonFactory = getJsonFactory();
            return new GCSConfig(
                    getNetHttpTransport(),
                    getScopes(),
                    getServiceAccountCredentials(jsonFactory),
                    jsonFactory,
                    m_GCSApplicationName,
                    m_BucketName);
        }

        private IServiceAccountCredentials getServiceAccountCredentials(final JsonFactory jsonFactory)
                throws BuildException {
            try {
                return new JsonCredentialsLoader(jsonFactory).load(m_JsonSecretsFile);
            } catch (IOException e) {
                throw new BuildException("JSON based secrets file couldn't be loaded!", e);
            }
        }

        private Set<String> getScopes() {
            return Collections.singleton(StorageScopes.DEVSTORAGE_FULL_CONTROL);
        }

        private NetHttpTransport getNetHttpTransport() throws BuildException {
            try {
                final String proxyHost = System.getProperty("http.proxyHost");
                final String proxyPort = System.getProperty("http.proxyPort");
                if (Strings.isEmpty(proxyHost) || Strings.isEmpty(proxyPort)) {
                    return GoogleNetHttpTransport.newTrustedTransport();
                } else {
                    return new NetHttpTransport.Builder()
                            .trustCertificates(GoogleUtils.getCertificateTrustStore())
                            .setProxy(
                                    new Proxy(
                                            Proxy.Type.HTTP,
                                            new InetSocketAddress(
                                                    proxyHost,
                                                    Integer.parseInt(proxyPort)
                                            )
                                    )
                            ).build();
                }
            } catch (GeneralSecurityException | IOException e) {
                throw new BuildException("Couldn't create HttpTransport!", e);
            }

        }

        private JsonFactory getJsonFactory() {
            return JacksonFactory.getDefaultInstance();
        }

        public Builder setJsonSecretsFile(final String jsonSecretsFile) {
            m_JsonSecretsFile = jsonSecretsFile;
            return this;
        }

        public Builder setGCSApplicationName(final String gcsApplicationName) {
            m_GCSApplicationName = gcsApplicationName;
            return this;
        }

        public Builder setBucketName(final String bucketName) {
            m_BucketName = bucketName;
            return this;
        }

    }
}