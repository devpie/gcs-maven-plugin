package de.janitza.maven.gcs

import java.nio.file.{Files, Path}

import de.janitza.maven.gcs.testsupport.{Permissions, TempFiles}
import org.apache.maven.plugin.logging.SystemStreamLog
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec

import scala.collection.mutable
import scala.compiletime.uninitialized

/**
  * FileFinder decides which files the plugin uploads, so anything it silently
  * misses or wrongly collects goes straight into the deploy.
  */
class FileFinderSpec extends AnyFreeSpec with BeforeAndAfterEach {

  private var root: Path = uninitialized

  override def beforeEach(): Unit = root = TempFiles.directory("gcs-filefinder-spec")

  override def afterEach(): Unit = TempFiles.deleteRecursively(root)

  private def matchesOf(globPattern: String): Seq[Path] = {
    val found = mutable.Buffer.empty[Path]
    Files.walkFileTree(root, new FileFinder(root, globPattern, new SystemStreamLog, found += _))
    found.toSeq
  }

  "a pattern without a path separator" - {

    "finds a matching file at any depth" in {
      val jar = TempFiles.fileAt(root, "target/app.jar")

      assert(matchesOf("*.jar") == Seq(jar))
    }

    // src/it/simple-it/pom.xml configures files-filter "*.jar" and expects the
    // build output below the base path to be picked up, however deep it sits.
    "finds a file several directories down, as the integration test configuration relies on" in {
      val jar = TempFiles.fileAt(root, "target/artifacts/app.jar")

      assert(matchesOf("*.jar") == Seq(jar))
    }

    "ignores files the pattern does not name" in {
      TempFiles.fileAt(root, "target/app.war")

      assert(matchesOf("*.jar").isEmpty)
    }
  }

  "a pattern with a path separator" - {

    "finds a file at any depth for **/*.jar" in {
      val jar = TempFiles.fileAt(root, "target/app.jar")

      assert(matchesOf("**/*.jar") == Seq(jar))
    }

    "finds a file in the directory the pattern names, as in target/*.jar" in {
      val jar = TempFiles.fileAt(root, "target/app.jar")

      assert(matchesOf("target/*.jar") == Seq(jar))
    }

    "ignores a file outside the directory the pattern names" in {
      TempFiles.fileAt(root, "other/app.jar")

      assert(matchesOf("target/*.jar").isEmpty)
    }

    // The separator in the pattern is literal, so **/*.jar demands at least one
    // directory level. Ant and Maven spell it the same way.
    "ignores a file directly in the root for **/*.jar, because the separator is literal" in {
      TempFiles.fileAt(root, "app.jar")

      assert(matchesOf("**/*.jar").isEmpty)
    }

    // Before separators worked, **.jar was the way to reach files in the depth.
    // Configurations that still spell it that way keep working.
    "keeps finding files for the workaround pattern **.jar, which carries no separator" in {
      val jar = TempFiles.fileAt(root, "target/app.jar")

      assert(matchesOf("**.jar") == Seq(jar))
    }
  }

  "only regular files are collected" - {

    "a directory whose name matches the pattern is skipped, but still entered" in {
      val file = TempFiles.fileAt(root, "app.jar")
      val nested = TempFiles.fileAt(root, "lib.jar/inner.jar")

      assert(matchesOf("*.jar").toSet == Set(file, nested))
    }

    "a symbolic link to a directory is skipped" in {
      val file = TempFiles.fileAt(root, "app.jar")
      val directory = Files.createDirectory(root.resolve("lib.jar"))
      Files.createSymbolicLink(root.resolve("link.jar"), directory)

      assert(matchesOf("*.jar").toSet == Set(file))
    }
  }
  "symbolic links" - {

    // walkFileTree runs without FOLLOW_LINKS, so the attrs handed to visitFile are those of
    // the link itself and report isRegularFile=false for both kinds of link. Asking
    // Files.isRegularFile instead follows the link, which is what the upload does too:
    // FileContent and Files.size both resolve it.
    "a link to a regular file is collected, because the upload can read it" in {
      val target = TempFiles.fileAt(root, "real/app.jar")
      val link = root.resolve("linked.jar")
      Files.createSymbolicLink(link, root.relativize(target))

      assert(matchesOf("*.jar").toSet == Set(target, link))
    }

    "a link to a directory is not collected, because opening it would fail" in {
      TempFiles.fileAt(root, "real/app.jar")
      val link = root.resolve("linkdir.jar")
      Files.createSymbolicLink(link, root.resolve("real"))

      assert(!matchesOf("*.jar").contains(link))
    }
  }

  "an entry the walk cannot read" - {

    // Without this the scan loses files without saying so and the deploy reports success
    // for an upload that is missing whatever sat below the unreadable directory.
    "is kept, so the caller learns the scan was incomplete" in {
      val readable = TempFiles.fileAt(root, "readable/app.jar")
      val unreadableDirectory = TempFiles.fileAt(root, "secret/hidden.jar").getParent

      Permissions.withoutAnyAccessTo(unreadableDirectory) {
        val found = mutable.Buffer.empty[Path]
        val finder = new FileFinder(root, "*.jar", new SystemStreamLog, found += _)
        Files.walkFileTree(root, finder)

        assert(finder.unreadablePaths == Seq(unreadableDirectory))
        assert(found.toSeq == Seq(readable))
      }
    }
  }

}
