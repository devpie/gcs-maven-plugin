package de.janitza.maven.gcs.testsupport

import java.io.IOException
import java.nio.file.Path
import java.nio.file.spi.FileTypeDetector

/**
  * A MIME type detector with fixed answers, so that tests do not depend on the
  * mime database of the operating system.
  *
  * Files.probeContentType asks every detector installed via ServiceLoader before
  * it falls back to the platform detector. This one is registered in
  * src/test/resources/META-INF/services/java.nio.file.spi.FileTypeDetector and
  * claims two invented extensions; everything else it passes on by answering null.
  *
  * It must be a class, not an object: an object compiles to X$ without a public
  * no-arg constructor, which the ServiceLoader rejects.
  */
class ProbeFileTypeDetector extends FileTypeDetector {
  override def probeContentType(path: Path): String = path.getFileName.toString match {
    case name if name.endsWith(ProbeFileTypeDetector.KnownExtension) => ProbeFileTypeDetector.KnownMimeType
    case name if name.endsWith(ProbeFileTypeDetector.FailingExtension) => throw new IOException("probe failed on purpose")
    case _ => null
  }
}

object ProbeFileTypeDetector {
  val KnownExtension = ".gcsprobe"
  val KnownMimeType = "application/x-gcs-probe"
  val FailingExtension = ".gcsfail"

  /**
    * Deliberately claimed by no detector, so that Files.probeContentType answers null
    * and the caller has to fall back. Two conditions carry that: no detector on the
    * test classpath claims the extension, and neither supported platform sniffs the
    * content for it — macOS has no entry for it, and cimg/openjdk:17.0 ships no
    * /usr/share/mime at all.
    */
  val UnknownExtension = ".gcsunknown"
}
