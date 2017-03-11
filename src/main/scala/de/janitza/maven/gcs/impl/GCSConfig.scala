package de.janitza.maven.gcs.impl.config

import java.io.IOException
import java.net.{InetSocketAddress, Proxy}

import com.google.api.client.googleapis.GoogleUtils
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.storage.StorageScopes
import de.janitza.maven.gcs.api.{BuildException, IGCSConfig, IServiceAccountCredentials}
import de.janitza.maven.gcs.impl.JsonCredentialsLoader
import de.janitza.maven.gcs.impl.util.Strings

object GCSConfig {

  class Builder {
    private var m_JsonSecretsFile: String = _
    private var m_GCSApplicationName: String = _
    private var m_BucketName: String = _

    @throws[BuildException]
    def build: GCSConfig = {
      val jsonFactory = getJsonFactory
      new GCSConfig(
        getNetHttpTransport,
        getScopes,
        getServiceAccountCredentials(jsonFactory),
        jsonFactory,
        m_GCSApplicationName,
        m_BucketName
      )
    }

    @throws[BuildException]
    private def getServiceAccountCredentials(jsonFactory: JsonFactory): IServiceAccountCredentials =
        new JsonCredentialsLoader(jsonFactory).load(m_JsonSecretsFile)

    private def getScopes = Seq(StorageScopes.DEVSTORAGE_FULL_CONTROL)

    @throws[BuildException]
    private def getNetHttpTransport: NetHttpTransport =
      try {
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")
        if (Strings.isEmpty(proxyHost) || Strings.isEmpty(proxyPort))
          GoogleNetHttpTransport.newTrustedTransport
        else
          new NetHttpTransport.Builder()
            .trustCertificates(GoogleUtils.getCertificateTrustStore)
            .setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort.toInt))).build
      } catch {
        case e: Any =>
          throw new BuildException("Couldn't create HttpTransport!", e)
      }

    private def getJsonFactory: JsonFactory = JacksonFactory.getDefaultInstance

    def setJsonSecretsFile(jsonSecretsFile: String) = {
      m_JsonSecretsFile = jsonSecretsFile
      this
    }

    def setGCSApplicationName(gcsApplicationName: String) = {
      m_GCSApplicationName = gcsApplicationName
      this
    }

    def setBucketName(bucketName: String) = {
      m_BucketName = bucketName
      this
    }
  }

}

case class GCSConfig private(httpTransport: HttpTransport,
                             scopes: Seq[String],
                             serviceAccountCredentials: IServiceAccountCredentials,
                             jsonFactory: JsonFactory,
                             gcsApplicationName: String,
                             bucketName: String
                            ) extends IGCSConfig
