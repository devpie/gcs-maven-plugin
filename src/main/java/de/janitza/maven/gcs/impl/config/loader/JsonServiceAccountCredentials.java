package de.janitza.maven.gcs.impl.config.loader;

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Key;
import com.google.common.base.Charsets;
import de.janitza.maven.gcs.api.config.loader.IJsonServiceAccountCredentials;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 13:55
 */
public final class JsonServiceAccountCredentials
        extends GenericJson
        implements IJsonServiceAccountCredentials {

    @Key ("client_email")
    private String m_ClientEmail;
    @Key ("private_key")
    private String m_PrivateKey;

    public static JsonServiceAccountCredentials load(
            final JsonFactory jsonFactory,
            final InputStream inputStream
    )
            throws IOException {
        return jsonFactory.fromInputStream(inputStream, Charsets.UTF_8,
                JsonServiceAccountCredentials.class);
    }

    public String getClientEmail() {
        return m_ClientEmail;
    }

    public void setClientEmail(String clientEmail) {
        this.m_ClientEmail = clientEmail;
    }

    public String getPrivateKey() {
        return m_PrivateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.m_PrivateKey = privateKey;
    }
}

