package de.janitza.maven.gcs.impl.config.loader;

import com.google.api.client.json.JsonFactory;
import de.janitza.maven.gcs.api.config.loader.IJsonCredentialsLoader;
import de.janitza.maven.gcs.api.config.loader.IServiceAccountCredentials;
import de.janitza.maven.gcs.api.exception.BuildException;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 11:59
 */
public class JsonCredentialsLoader implements IJsonCredentialsLoader {

    private final JsonFactory m_JsonFactory;

    public JsonCredentialsLoader(final JsonFactory jsonFactory) {
        m_JsonFactory = jsonFactory;
    }

    @Override
    public IServiceAccountCredentials load(final String jsonSecretsFile)
            throws BuildException, IOException {
        final IServiceAccountCredentials serviceAccountCredentials =
                new ServiceAccountCredentials.Builder().setJsonServiceAccountCredentials(
                        getServiceAccountCredentials(jsonSecretsFile)
                ).build();
        return serviceAccountCredentials;
    }

    private JsonServiceAccountCredentials getServiceAccountCredentials(
            final String jsonSecretsFile
    ) throws IOException {
        return JsonServiceAccountCredentials.load(
                m_JsonFactory,
                new FileInputStream(jsonSecretsFile)
        );
    }


}
