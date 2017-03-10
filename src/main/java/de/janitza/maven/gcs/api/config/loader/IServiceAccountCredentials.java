package de.janitza.maven.gcs.api.config.loader;

import java.security.PrivateKey;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 12:24
 */
public interface IServiceAccountCredentials {
    String getAccountId();

    PrivateKey getPrivateKey();
}
