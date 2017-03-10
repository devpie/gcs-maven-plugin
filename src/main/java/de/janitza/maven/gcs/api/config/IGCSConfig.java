package de.janitza.maven.gcs.api.config;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import de.janitza.maven.gcs.api.config.loader.IServiceAccountCredentials;

import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 12:03
 */
public interface IGCSConfig {
    JsonFactory getJsonFactory();

    HttpTransport getHttpTransport();

    Collection<String> getScopes();

    IServiceAccountCredentials getServiceAccountCredentials();

    String getGCSApplicationName();

    String getBucketName();
}
