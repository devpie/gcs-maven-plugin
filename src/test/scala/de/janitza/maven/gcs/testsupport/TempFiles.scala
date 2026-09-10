package de.janitza.maven.gcs.testsupport

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.Comparator

/**
  * Temporary trees for tests. Each suite creates its own root and deletes it in
  * afterAll or afterEach — nothing here registers a cleanup hook.
  */
object TempFiles {

  def directory(prefix: String): Path = Files.createTempDirectory(prefix)

  /**
    * Writes a file below `root`. The relative path may contain separators; missing
    * intermediate directories are created.
    */
  def fileAt(root: Path, relativePath: String, content: String = "hello"): Path = {
    val file = root.resolve(relativePath)
    Files.createDirectories(file.getParent)
    Files.write(file, content.getBytes(UTF_8))
  }

  def deleteRecursively(root: Path): Unit =
    if (Files.exists(root)) {
      Files.walk(root).sorted(Comparator.reverseOrder[Path]()).forEach(Files.delete(_))
    }
}
