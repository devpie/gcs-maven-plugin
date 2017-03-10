package de.janitza.maven.gcs.api.config.loader;

import de.janitza.maven.gcs.api.exception.BuildException;
import de.janitza.maven.gcs.api.exception.ValidationException;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 14:10
 */
public interface IJsonCredentialsLoader {
    IServiceAccountCredentials load(String jsonSecretsFile)
            throws IOException, ValidationException, BuildException;
}
