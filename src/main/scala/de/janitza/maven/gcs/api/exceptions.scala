package de.janitza.maven.gcs.api

class BuildException(message: String, throwable: Throwable) extends Exception(message, throwable) {
  def this(message: String) {
    this(message, null)
  }
}