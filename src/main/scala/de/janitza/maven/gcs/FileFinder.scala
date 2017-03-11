package de.janitza.maven.gcs

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import org.apache.maven.plugin.logging.Log

/**
  * Created by jan on 11.03.17.
  */
class FileFinder(val globPattern: String, val m_log: Log, val pathAction: (Path) => Unit) extends SimpleFileVisitor[Path] {
  private val m_pathMatcher = FileSystems.getDefault.getPathMatcher("glob:" + globPattern)

  private[gcs] def find(file: Path) {
    Option(file.getFileName).foreach((name) =>
      if(m_pathMatcher.matches(name)) {
        m_log.info("Found file: " + file)
        pathAction(file)
      }
    )
  }

  override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
    find(file)
    FileVisitResult.CONTINUE
  }

  override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
    find(dir)
    FileVisitResult.CONTINUE
  }

  override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
    m_log.error(exc)
    FileVisitResult.CONTINUE
  }
}
