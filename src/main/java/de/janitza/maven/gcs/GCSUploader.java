/**
 * Copyright 2010 - 2015 Janitza electronics GmbH
 *
 * @author Jan Marco Müller <jan.mueller@janitza.de>
 */
package de.janitza.maven.gcs;

import de.janitza.maven.gcs.api.IGoogleCloudStorageService;
import de.janitza.maven.gcs.api.exception.BuildException;
import de.janitza.maven.gcs.impl.GoogleCloudStorageService;
import de.janitza.maven.gcs.impl.config.GCSConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Goal which uploads files into a specified bucket of the Google Cloud Storage
 */
@Mojo (name = "upload", defaultPhase = LifecyclePhase.DEPLOY)
public class GCSUploader extends AbstractMojo {
    /**
     * Location of the build directory.
     */
    @Parameter (
            defaultValue = "${project.basedir}",
            property = "base-directory",
            required = false,
            alias = "base-directory"
    )
    private File m_BaseDir;

    /**
     * Location of the file.
     */
    @Parameter (
            defaultValue = "${gcs-application-name}",
            property = "gcs-application-name",
            required = true,
            alias = "gcs-application-name"
    )
    private String m_ApplicationName;

    /**
     * A filter expression identifying the files to be uploaded.
     */
    @Parameter (
            defaultValue = "${files-filter}", // download/5.1.3-m1/*
            property = "files-filter",
            required = true,
            alias = "files-filter"
    )
    private String m_FilesFilter;

    /**
     * A GCS bucket uri for uploading the files to.
     */
    @Parameter (
            defaultValue = "${bucket-name}", // gridvis.janitza.de
            property = "bucket-name",
            required = true,
            alias = "bucket-name"
    )
    private String m_BucketName;

    /**
     * The GCS secrets file.
     */
    @Parameter (
            defaultValue = "${project.basedir}/src/main/gcs/secret.json",
            property = "json-secrets-file",
            required = true,
            alias = "json-secrets-file"
    )
    private String m_JsonSecretsFile;

    /**
     * If the files should be shared publicly.
     */
    @Parameter (
            defaultValue = "${share-public}", // true
            property = "share-public",
            required = true,
            alias = "share-public"
    )
    private boolean m_SharePublic;

    /**
     * The relative base path within the google bucket.
     */
    @Parameter (
            defaultValue = "${bucket-base-path}", // download/5.1.3-m2
            property = "bucket-base-path",
            required = false,
            alias = "bucket-base-path"
    )
    private String m_BaseBucketPath;

    /**
     * The root path for recursively applying the files filter to.
     */
    @Parameter (
            defaultValue = "${files-filter-base-path}", // download/5.1.3-m1/*
            property = "files-filter-base-path",
            required = false,
            alias = "files-filter-base-path"
    )
    private String m_FilesFilterBasePath;

    public void execute()
            throws MojoExecutionException {

        try {

            final IGoogleCloudStorageService service = getGoogleCloudStorageService();
            final List<Path> pomFiles = new LinkedList<>();

            Files.walkFileTree(
                    Paths.get(m_FilesFilterBasePath),
                    new FileFinder(
                            m_FilesFilter,
                            getLog(),
                            (path) -> pomFiles.add(path)));

            for (final Path path : pomFiles) {
                uploadFile(service, path);
            }
        } catch (BuildException e) {
            throw new MojoExecutionException("Config not buildable!", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Scanning the files and uploading them" +
                    " resulted in an error!", e);
        }

    }

    private void processSuppressedExceptions(final List<IOException> suppressedExceptions)
            throws MojoExecutionException {
        if (!suppressedExceptions.isEmpty()) {
            final MojoExecutionException mojoExecutionException = new
                    MojoExecutionException("Some uploads resultet in an error!");
            suppressedExceptions.forEach(e -> mojoExecutionException.addSuppressed(e));
            throw mojoExecutionException;
        }
    }

    private GoogleCloudStorageService getGoogleCloudStorageService()
            throws BuildException, IOException {
        return new GoogleCloudStorageService(getGCSConfig(), getLog());
    }

    private GCSConfig getGCSConfig() throws BuildException {
        return new GCSConfig.Builder()
                .setGCSApplicationName(m_ApplicationName)
                .setBucketName(m_BucketName)
                .setJsonSecretsFile(m_JsonSecretsFile)
                .build();
    }

    private void uploadFile(
            final IGoogleCloudStorageService service,
            final Path path
    )
            throws IOException {
        service.uploadFile(path, Optional.ofNullable(m_BaseBucketPath),
                m_SharePublic);
    }
}
