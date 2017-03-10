package de.janitza.maven.gcs.impl.config.loader;

import com.google.api.client.util.PemReader;
import de.janitza.maven.gcs.api.config.loader.IJsonServiceAccountCredentials;
import de.janitza.maven.gcs.api.config.loader.IServiceAccountCredentials;
import de.janitza.maven.gcs.api.exception.BuildException;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 14:18
 */
public class ServiceAccountCredentials implements IServiceAccountCredentials {

    private final String m_AccountId;
    private final PrivateKey m_PrivateKey;

    private ServiceAccountCredentials(
            final String accountId,
            final PrivateKey privateKey
    ) {
        m_AccountId = accountId;
        m_PrivateKey = privateKey;
    }

    @Override
    public String getAccountId() {
        return m_AccountId;
    }

    @Override
    public PrivateKey getPrivateKey() {
        return m_PrivateKey;
    }

    public static class Builder {
        private IJsonServiceAccountCredentials m_JsonServiceAccountCredentials;

        public Builder setJsonServiceAccountCredentials(
                final IJsonServiceAccountCredentials jsonServiceAccountCredentials
        ) {
            m_JsonServiceAccountCredentials = jsonServiceAccountCredentials;
            return this;
        }

        public IServiceAccountCredentials build() throws BuildException {
            return new ServiceAccountCredentials(getAccountId(), getPrivateKey());
        }

        private String getAccountId() throws BuildException {
            final String accountId = m_JsonServiceAccountCredentials.getClientEmail();
            if (accountId == null) {
                throw new BuildException("The client_email is missing ");
            }
            return accountId;
        }

        private PrivateKey getPrivateKey() throws BuildException {
            String privateKey = m_JsonServiceAccountCredentials.getPrivateKey();
            if (privateKey != null && !privateKey.isEmpty()) {
                PemReader pemReader = new PemReader(new StringReader(privateKey));
                try {
                    PemReader.Section section = pemReader.readNextSection();
                    PKCS8EncodedKeySpec keySpec =
                            new PKCS8EncodedKeySpec(section.getBase64DecodedBytes());
                    return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
                } catch (final IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
                    throw new BuildException("The supplied credentials are not valid!", e);
                }
            }
            throw new BuildException("The private key within the credentials is missing!");
        }
    }

    @Override
    public String toString() {
        return "ServiceAccountCredentials{" +
                "m_AccountId='" + m_AccountId + '\'' +
                ", m_PrivateKey=" + m_PrivateKey +
                '}';
    }
}
