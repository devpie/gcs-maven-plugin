package de.janitza.maven.gcs.impl.util

import java.nio.file.Path

import de.janitza.maven.gcs.api.{Error, Success}
import de.janitza.maven.gcs.testsupport.{ProbeFileTypeDetector, TempFiles}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import scala.compiletime.uninitialized

/**
  * The mime type assertions do not depend on the operating system.
  *
  * Files.probeContentType asks every detector registered via ServiceLoader before
  * it falls back to the platform detector, and the test classpath contains
  * ProbeFileTypeDetector. Its answers are the same everywhere, which the previous
  * version of this suite was not: it expected "text/x-scala", which Linux happens
  * to know and macOS does not.
  *
  * Files.probeContentType resolves the ServiceLoader against the system class
  * loader, which only sees target/test-classes when the tests run in their own
  * JVM. That is what forkMode=once in the pom.xml is for.
  */
class HttpUtilSpec extends AnyFreeSpec with BeforeAndAfterAll {

  private val DetectorMissing =
    "The test detector is not installed. Are the tests running in their own JVM? See forkMode in the pom.xml."

  private var tempDir: Path = uninitialized

  override def beforeAll(): Unit = tempDir = TempFiles.directory("gcs-httputil-spec")

  override def afterAll(): Unit = TempFiles.deleteRecursively(tempDir)

  "getMimeType" - {

    "returns the type the registered detector reports" in {
      val file = TempFiles.fileAt(tempDir, s"report${ProbeFileTypeDetector.KnownExtension}")

      withClue(DetectorMissing) {
        assert(HttpUtil.getMimeType(file) == Success(ProbeFileTypeDetector.KnownMimeType))
      }
    }

    "returns an Error naming the file when probing throws" in {
      val file = TempFiles.fileAt(tempDir, s"broken${ProbeFileTypeDetector.FailingExtension}")

      withClue(DetectorMissing) {
        HttpUtil.getMimeType(file) match {
          case Error(message, exception) =>
            assert(message.contains(file.toString))
            assert(exception.isDefined)
          case other => fail(s"expected an Error, got $other")
        }
      }
    }

    // Files.probeContentType answers null whenever no detector recognises the file.
    // That is the everyday case for a Maven deploy: .pom, .sha1 and .md5 are unknown
    // to both supported platforms. An Error here would abort every deploy at the first
    // checksum file, so the type falls back instead of failing.
    // The expected value is spelled out rather than read from HttpUtil, because it is
    // the contract towards Google Cloud Storage, not an internal detail.
    "falls back to application/octet-stream when no detector recognises the file" in {
      val file = TempFiles.fileAt(tempDir, s"anything${ProbeFileTypeDetector.UnknownExtension}")

      assert(HttpUtil.getMimeType(file) == Success("application/octet-stream"))
    }
  }

  "getContentDisposition" - {

    "wraps the file name in quotes" in {
      assert(HttpUtil.getContentDisposition("test.txt") == """attachment; filename="test.txt"""")
    }

    "keeps spaces in the file name" in {
      assert(HttpUtil.getContentDisposition("my report.pdf") == """attachment; filename="my report.pdf"""")
    }

    // RFC 6266 puts the file name into a quoted-string, where " has to be escaped.
    // Unescaped it would end the header value early and leave the rest as garbage.
    "escapes a quote in the file name" in {
      assert(HttpUtil.getContentDisposition("he\"llo.txt") == "attachment; filename=\"he\\\"llo.txt\"")
    }

    // The only case that tells the two escaping orders apart. Escaping \ first gives
    // \\ \" — three backslashes and a quote; escaping " first would escape the
    // backslash of that escape a second time and produce four.
    "escapes a backslash before the quote it precedes" in {
      assert(HttpUtil.getContentDisposition("a\\\"b.txt") == "attachment; filename=\"a\\\\\\\"b.txt\"")
    }

    // A line break cannot be escaped into a quoted-string; carried through it would end
    // the header and let the file name append a header of its own. Dropping it is the
    // smallest change that makes that impossible without inventing characters.
    "drops a line break from the file name" in {
      assert(HttpUtil.getContentDisposition("line\r\nbreak.txt") == """attachment; filename="linebreak.txt"""")
    }
  }
}
