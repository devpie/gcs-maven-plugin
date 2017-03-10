package de.janitza.maven.gcs.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 14:42
 */
public interface IGoogleCloudStorageService {

    void uploadFile(
            Path file,
            Optional<String> relativePathInStorage,
            boolean sharePublic
    ) throws IOException;
}
