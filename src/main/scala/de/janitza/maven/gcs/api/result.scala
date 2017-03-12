package de.janitza.maven.gcs.api

/**
  * Created by jan on 12.03.17.
  */
sealed trait Result[+T]

case class Success[T](value: T) extends Result[T]
case class Error(message: String, exception: Option[Throwable] = None) extends Result[Nothing]