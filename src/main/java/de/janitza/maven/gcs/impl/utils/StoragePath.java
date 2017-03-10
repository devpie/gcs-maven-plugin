package de.janitza.maven.gcs.impl.utils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 11.02.15
 * Time: 08:21
 */
public final class StoragePath {

    public static final String PATH_SEPARATOR = "/";

    private StoragePath() {
    }

    public static String join(final List<String> pathParts) {
        return pathParts.stream().collect(Collectors.joining(PATH_SEPARATOR));
    }

    public static String join(
            final List<String> pathParts,
            final String filename
    ) {
        return join(Lists.concat(pathParts, filename));
    }

    public static String join(
            final String first,
            final String fileName
    ) {
        return first.endsWith(PATH_SEPARATOR) ? first + fileName : first + PATH_SEPARATOR + fileName;
    }
}