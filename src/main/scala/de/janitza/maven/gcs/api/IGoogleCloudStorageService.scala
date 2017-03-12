package de.janitza.maven.gcs.api

import java.io.IOException
import java.nio.file.Path

/**
  * Created by jan on 11.03.17.
  */
trait IGoogleCloudStorageService {
  def uploadFile(file: Path, relativePathInStorage: Option[String], sharePublic: Boolean): Result[Unit]
}
