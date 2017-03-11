package de.janitza.maven.gcs.impl.util

import java.io.IOException
import java.nio.file.{Files, Path}

object HttpUtil {
  def getContentDisposition(filename: String) = s"""attachment; filename="$filename""""

  @throws[IOException]
  def getMimeType(path: Path) = Files.probeContentType(path)
}

object StoragePath {
  val PATH_SEPARATOR = "/"

  def join(pathParts: Seq[String]) = pathParts.mkString(PATH_SEPARATOR)

  def join(pathParts: Seq[String], filename: String): String = join(pathParts :+ filename)

  def join(first: String, fileName: String) =
    if (first.endsWith(PATH_SEPARATOR)) first + fileName
    else first + PATH_SEPARATOR + fileName
}

object Strings {
  def isEmpty(string: String) = string == null || string.isEmpty
}