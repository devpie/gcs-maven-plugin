package de.janitza.maven.gcs

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import org.apache.maven.plugin.logging.Log

/**
  * Collects the regular files below `rootDirectory` that match `globPattern` and remembers
  * every entry it could not read, so the caller can tell a complete scan from an
  * incomplete one.
  *
  * Created by jan on 11.03.17.
  */
class FileFinder(
  val rootDirectory: Path,
  val globPattern: String,
  val m_log: Log,
  val pathAction: (Path) => Unit
) extends SimpleFileVisitor[Path] {

  /** java.nio glob patterns always use "/", whatever the platform separator is. */
  private val GlobPathSeparator = "/"

  private val m_unreadablePaths = collection.mutable.ArrayBuffer.empty[Path]

  private val m_pathMatcher = FileSystems.getDefault.getPathMatcher("glob:" + globPattern)

  /**
    * A pattern without a separator names a file, not a location, so it is matched
    * against the file name alone and hits at any depth. Matching such a pattern
    * against the relative path instead would restrict it to the root directory and
    * break every existing configuration.
    */
  private val m_matchesRelativePath = globPattern.contains(GlobPathSeparator)

  private def pathToMatch(file: Path): Path =
    if (m_matchesRelativePath) rootDirectory.relativize(file) else file.getFileName

  private[gcs] def find(file: Path): Unit = {
    Option(pathToMatch(file)).filter(m_pathMatcher.matches).foreach(_ => {
      m_log.info("Found file: " + file)
      pathAction(file)
    })
  }

  override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
    if (Files.isRegularFile(file)) find(file)
    FileVisitResult.CONTINUE
  }

  /**
    * The entries the walk could not read, in the order it hit them. Empty until the walk
    * has run. A caller that ignores them works on an incomplete list of files.
    */
  def unreadablePaths: Seq[Path] = m_unreadablePaths.toSeq

  /**
    * The walk goes on, so one unreadable directory does not hide the rest of the tree from
    * the caller. Remembering the path is what lets the caller decide what to do with it;
    * the log line alone is easy to miss in a build log.
    */
  override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
    m_log.error(exc)
    m_unreadablePaths += file
    FileVisitResult.CONTINUE
  }
}
