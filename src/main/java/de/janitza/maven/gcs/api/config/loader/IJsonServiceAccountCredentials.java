package de.janitza.maven.gcs.api.config.loader;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 13:59
 */
public interface IJsonServiceAccountCredentials {
    String getClientEmail();

    String getPrivateKey();
}
