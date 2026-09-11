package de.janitza.maven.gcs.testsupport

import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermissions}
import java.nio.file.{AccessDeniedException, Files, Path}

import org.scalatest.Assertions

/**
  * File permissions a test needs for the duration of one test body.
  */
object Permissions extends Assertions {

  private val OwnerOnly = PosixFilePermissions.fromString("rwx------")

  /**
    * Takes every permission off `directory` while `body` runs and puts them back
    * afterwards, because a test cannot delete a directory it may not read.
    *
    * Cancels the test when mode 000 does not actually take effect - as root, or on a
    * filesystem that ignores permissions, the directory stays readable and the test would
    * report success without ever having exercised the failure.
    */
  def withoutAnyAccessTo[T](directory: Path)(body: => T): T = {
    if (!Files.getFileStore(directory).supportsFileAttributeView(classOf[PosixFileAttributeView])) {
      cancel(s"$directory lies on a filesystem without POSIX permissions")
    }
    Files.setPosixFilePermissions(directory, java.util.Set.of())
    if (isReadable(directory)) {
      restoreOwnerAccessTo(directory)
      cancel(s"$directory is readable despite mode 000 - the test runs as root or on a filesystem that ignores permissions")
    }
    try body
    finally restoreOwnerAccessTo(directory)
  }

  /** The same for several directories at once, all of them unreadable while `body` runs. */
  def withoutAnyAccessTo[T](directories: Seq[Path])(body: => T): T =
    if (directories.isEmpty) body
    else withoutAnyAccessTo(directories.head)(withoutAnyAccessTo(directories.tail)(body))

  private def isReadable(directory: Path): Boolean =
    try {
      Files.newDirectoryStream(directory).close()
      true
    } catch {
      case _: AccessDeniedException => false
    }

  private def restoreOwnerAccessTo(directory: Path): Unit =
    Files.setPosixFilePermissions(directory, OwnerOnly)
}
