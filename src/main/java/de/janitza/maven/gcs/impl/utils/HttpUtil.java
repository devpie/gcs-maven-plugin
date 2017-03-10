package de.janitza.maven.gcs.impl.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 11.02.15
 * Time: 09:34
 */
public final class HttpUtil {
    private HttpUtil() {
    }

    public static String getContentDisposition(final String filename) {
        return String.format("attachment; filename=\"%s\"", filename);
    }

    public static String getMimeType(final Path path) throws IOException {
        return Files.probeContentType(path);
    }
}
