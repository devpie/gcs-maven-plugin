package de.janitza.maven.gcs.impl.util

import java.io.IOException
import java.nio.file.{Files, Path}

import de.janitza.maven.gcs.api.{Error, Result, Success}

/**
  * Created by jan on 12.03.17.
  */

object HttpUtil {

  /** What a payload is called whose type nobody can name. */
  val DEFAULT_MIME_TYPE = "application/octet-stream"

  def getContentDisposition(fileName: String): String =
    s"""attachment; filename="${asQuotedString(fileName)}""""

  def getMimeType(path: Path): Result[String] =
    try {
      // probeContentType answers null for every file no detector recognises. A Maven
      // deploy runs into that with its first .pom, .sha1 or .md5, so an unknown type
      // falls back instead of failing the upload.
      Success(Option(Files.probeContentType(path)) getOrElse DEFAULT_MIME_TYPE)
    } catch {
      case e: IOException => Error(s"Getting the mime type of $path failed!", Some(e))
    }

  /**
    * RFC 6266 carries the file name in a quoted-string, so \ and " have to be escaped.
    * The backslash goes first, otherwise the escape added for the quote gets escaped a
    * second time.
    *
    * Control characters are dropped rather than escaped. Not because of the request that
    * carries them — this value travels to the API inside a JSON body, where Jackson
    * escapes them and no header can arise. It matters at the other end: Cloud Storage
    * replays contentDisposition as an HTTP header when the object is downloaded, and a
    * CR or LF has no business in a header value. None of the ISO control range means
    * anything in a file name, so dropping takes nothing away.
    */
  private def asQuotedString(fileName: String): String =
    fileName
      .filterNot(Character.isISOControl)
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
}

object StoragePath {
  val PATH_SEPARATOR = "/"

  def join(prefix: String, fileName: String): String =
    if (prefix.endsWith(PATH_SEPARATOR)) prefix + fileName
    else prefix + PATH_SEPARATOR + fileName
}

object Strings {
  def isEmpty(string: String) = string == null || string.isEmpty
}
