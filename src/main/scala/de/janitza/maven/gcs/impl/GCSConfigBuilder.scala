package de.janitza.maven.gcs.impl.config

import java.net.{InetSocketAddress, Proxy}

import com.google.api.client.googleapis.GoogleUtils
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.storage.StorageScopes
import de.janitza.maven.gcs.api.config.{GCSConfig, ServiceAccountCredentials}
import de.janitza.maven.gcs.api.{Error, Result, Success}
import de.janitza.maven.gcs.impl.util.Strings

case class GCSConfigBuilder(jsonSecretsFile: String, gcsApplicationName: String, bucketName: String) {

  def create(jsonFactory: JsonFactory, netHttpTransport: NetHttpTransport, credentials: ServiceAccountCredentials): GCSConfig =
    GCSConfig(
      netHttpTransport,
      getScopes,
      credentials,
      jsonFactory,
      gcsApplicationName,
      bucketName
    )

  def build: Result[GCSConfig] = {
    val jsonFactory = getJsonFactory
    getNetHttpTransport match {
      case Success(netHttpTransport) => getServiceAccountCredentials match {
        case Success(credentials) => Success(create(jsonFactory, netHttpTransport, credentials))
        case e: Error => e
      }
      case e: Error => e
    }
  }

  private def getServiceAccountCredentials = ServiceAccountCredentials.load(jsonSecretsFile)

  private def getScopes = Seq(StorageScopes.DEVSTORAGE_FULL_CONTROL)

  private def getNetHttpTransport: Result[NetHttpTransport] =
    try {
      val proxyHost = System.getProperty("http.proxyHost")
      val proxyPort = System.getProperty("http.proxyPort")
      if (Strings.isEmpty(proxyHost) || Strings.isEmpty(proxyPort))
        Success(GoogleNetHttpTransport.newTrustedTransport)
      else
        Success(
          new NetHttpTransport.Builder()
            .trustCertificates(GoogleUtils.getCertificateTrustStore)
            .setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort.toInt)))
            .build
        )
    } catch {
      case e: Any => Error("Couldn't create HttpTransport!", Some(e))
    }

  private def getJsonFactory: JsonFactory = JacksonFactory.getDefaultInstance

}
