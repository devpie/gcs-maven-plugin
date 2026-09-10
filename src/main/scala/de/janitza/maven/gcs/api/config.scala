package de.janitza.maven.gcs.api.config

import java.io.StringReader
import java.nio.file.{Files, Paths}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey}

import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.util.PemReader
import de.janitza.maven.gcs.api.{Error, Result, Success}
import de.janitza.maven.gcs.impl.util.Strings
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}

import scala.util.control.NonFatal

case class GCSConfig(httpTransport: HttpTransport,
                             scopes: Seq[String],
                             serviceAccountCredentials: ServiceAccountCredentials,
                             jsonFactory: JsonFactory,
                             gcsApplicationName: String,
                             bucketName: String)


private[config] case class JsonServiceAccountCredentials(client_email: String, private_key: String)

private[config] object JsonServiceAccountCredentials {

  private val reader = Json.reads[JsonServiceAccountCredentials]

  def load(path: String): Result[JsonServiceAccountCredentials] =
    parse(path) match {
      case Success(json) => read(json)
      case e: Error => e
    }

  private def read(json: JsValue): Result[JsonServiceAccountCredentials] =
    reader.reads(json) match {
      case JsSuccess(credentials, _) => Success(credentials)
      case e: JsError => Error(e.toString)
    }

  /**
    * The caller expects a Result, so reading and parsing must not throw. A missing file
    * and unparsable content are the two everyday mistakes, and both messages name the
    * path: that is what points a user at a typo in json-secrets-file.
    *
    * readAllBytes closes the file itself, and Json.parse takes the byte array directly,
    * so no stream outlives this method. A secrets file is a few kilobytes.
    */
  private def parse(path: String): Result[JsValue] =
    readAllBytes(path) match {
      case Success(content) =>
        try Success(Json.parse(content))
        catch {
          case NonFatal(e) => Error(s"The credentials file '$path' does not contain valid JSON!", Some(e))
        }
      case e: Error => e
    }

  private def readAllBytes(path: String): Result[Array[Byte]] =
    try Success(Files.readAllBytes(Paths.get(path)))
    catch {
      case NonFatal(e) => Error(s"The credentials file '$path' could not be read!", Some(e))
    }
}


case class ServiceAccountCredentials(accountId: String, privateKey: PrivateKey)

object ServiceAccountCredentials {

  private val InvalidCredentialsMessage = "The supplied credentials are not valid!"

  private val MissingPrivateKeyMessage = "The private key within the credentials is missing!"

  private val KeyAlgorithm = "RSA"

  def load(path: String): Result[ServiceAccountCredentials] =
    JsonServiceAccountCredentials.load(path) match {
      case Success(value) => create(value)
      case e: Error => e
    }

  private def create(credentials: JsonServiceAccountCredentials): Result[ServiceAccountCredentials] = {
    getPrivateKey(credentials) match {
      case Success(value: PrivateKey) => Success(ServiceAccountCredentials(credentials.client_email, value))
      case e: Error => e
    }
  }

  private def getPrivateKey(credentials: JsonServiceAccountCredentials): Result[PrivateKey] = {
    val privateKey = credentials.private_key
    if (!Strings.isEmpty(privateKey)) {
      val pemReader = new PemReader(new StringReader(privateKey))
      // NonFatal rather than Any: Any also catches VirtualMachineError, LinkageError,
      // InterruptedException and ControlThrowable, which have nothing to do with the
      // credentials and would be reported as if they had.
      try {
        val section = pemReader.readNextSection
        // readNextSection answers null when the text holds no PEM block at all.
        if (section == null) Error(InvalidCredentialsMessage)
        else {
          val keySpec = new PKCS8EncodedKeySpec(section.getBase64DecodedBytes)
          Success(KeyFactory.getInstance(KeyAlgorithm).generatePrivate(keySpec))
        }
      } catch {
        case NonFatal(e) => Error(InvalidCredentialsMessage, Some(e))
      }
    } else {
      Error(MissingPrivateKeyMessage)
    }
  }
}
