package de.janitza.maven.gcs.impl.util

import java.io.IOException
import java.nio.file.{Files, Path}

import de.janitza.maven.gcs.api.{Error, Result, Success}

/**
  * Created by jan on 12.03.17.
  */

object HttpUtil {
  def getContentDisposition(filename: String) = s"""attachment; filename="$filename""""

  def getMimeType(path: Path): Result[String] =
    try {
      Success(Files.probeContentType(path))
    } catch {
      case e: IOException => Error(s"Getting the mime type of $path failed!", Some(e))
    }
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
