package de.janitza.maven.gcs;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 12.02.15
 * Time: 09:50
 */
public class FileFinder extends SimpleFileVisitor<Path> {

    private final PathMatcher m_pathMatcher;
    private final Log m_log;
    private final Consumer<Path> m_pathAction;

    public FileFinder(
            final String globPattern,
            final Log log,
            final Consumer<Path>
                    pathAction
    ) {
        m_log = log;
        m_pathAction = pathAction;
        m_pathMatcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + globPattern);
    }

    void find(final Path file) {
        final Path name = file.getFileName();
        if (name != null && m_pathMatcher.matches(name)) {
            m_log.info("Found file: " + file);
            m_pathAction.accept(file);
        }
    }

    @Override
    public FileVisitResult visitFile(
            final Path file,
            final BasicFileAttributes attrs
    ) {
        find(file);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(
            final Path dir,
            final BasicFileAttributes attrs
    ) {
        find(dir);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(
            final Path file,
            final IOException exc
    ) {
        m_log.error(exc);
        return FileVisitResult.CONTINUE;
    }
}
