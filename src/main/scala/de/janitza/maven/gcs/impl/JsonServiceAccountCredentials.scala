package de.janitza.maven.gcs.impl

import java.io.{IOException, InputStream}
import java.nio.charset.StandardCharsets

import com.google.api.client.json.{GenericJson, JsonFactory}
import com.google.api.client.util.Key
import de.janitza.maven.gcs.api.IJsonServiceAccountCredentials


/**
  * Created by jan on 11.03.17.
  */
object JsonServiceAccountCredentials {
  @throws[IOException]
  def load(jsonFactory: JsonFactory, inputStream: InputStream): JsonServiceAccountCredentials =
    jsonFactory.fromInputStream(inputStream, StandardCharsets.UTF_8, classOf[JsonServiceAccountCredentials])
}

class JsonServiceAccountCredentials
  extends GenericJson with IJsonServiceAccountCredentials {

  @Key("client_email")
  private var m_clientEmail: String = _

  @Key("private_key")
  private var m_privateKey: String = _

  override def getClientEmail: String = m_clientEmail

  override def getPrivateKey: String = m_privateKey

  override def toString = s"$m_clientEmail :: $m_privateKey"
}