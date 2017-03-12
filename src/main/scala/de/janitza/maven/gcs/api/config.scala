package de.janitza.maven.gcs.api.config

import java.io.{FileInputStream, StringReader}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey}

import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.util.PemReader
import de.janitza.maven.gcs.api.{Error, Result, Success}
import de.janitza.maven.gcs.impl.util.Strings
import play.api.libs.json.{JsError, JsSuccess, Json}

case class GCSConfig private(httpTransport: HttpTransport,
                             scopes: Seq[String],
                             serviceAccountCredentials: ServiceAccountCredentials,
                             jsonFactory: JsonFactory,
                             gcsApplicationName: String,
                             bucketName: String)


private[config] case class JsonServiceAccountCredentials(client_email: String, private_key: String)

private[config] object JsonServiceAccountCredentials {
  def reader = Json.reads[JsonServiceAccountCredentials]

  def load(path: String) = {
    reader.reads(Json.parse(new FileInputStream(path)))
  }
}


case class ServiceAccountCredentials(accountId: String, privateKey: PrivateKey)

object ServiceAccountCredentials {
  def load(path: String): Result[ServiceAccountCredentials] = {
    JsonServiceAccountCredentials.load(path) match {
      case JsSuccess(value: JsonServiceAccountCredentials, _) => create(value)
      case e: Error => e
    }
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
      try {
        val section = pemReader.readNextSection
        val keySpec = new PKCS8EncodedKeySpec(section.getBase64DecodedBytes)
        Success(KeyFactory.getInstance("RSA").generatePrivate(keySpec))
      } catch {
        case e: Any => Error("The supplied credentials are not valid!", Some(e))
      }
    } else {
      Error("The private key within the credentials is missing!")
    }
  }
}