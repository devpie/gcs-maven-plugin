package de.janitza.maven.gcs.impl

import java.io.{FileInputStream, IOException, StringReader}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey}

import com.google.api.client.json.JsonFactory
import com.google.api.client.util.PemReader
import de.janitza.maven.gcs.api.{BuildException, IJsonCredentialsLoader, IJsonServiceAccountCredentials, IServiceAccountCredentials}
import de.janitza.maven.gcs.impl.util.Strings

class JsonCredentialsLoader(val m_JsonFactory: JsonFactory) extends IJsonCredentialsLoader {
  @throws[BuildException]
  def load(jsonSecretsFile: String): IServiceAccountCredentials = {
    try {
      new ServiceAccountCredentials.Builder()
        .setJsonServiceAccountCredentials(getServiceAccountCredentials(jsonSecretsFile))
        .build
    } catch {
      case e: IOException => throw new BuildException("Credentials couldn't be loaded!", e)
    }
  }

  @throws[IOException]
  private def getServiceAccountCredentials(jsonSecretsFile: String): IJsonServiceAccountCredentials =
    JsonServiceAccountCredentials.load(m_JsonFactory, new FileInputStream(jsonSecretsFile))
}

object ServiceAccountCredentials {

  class Builder {
    private var m_JsonServiceAccountCredentials: IJsonServiceAccountCredentials = _

    def setJsonServiceAccountCredentials(jsonServiceAccountCredentials: IJsonServiceAccountCredentials) = {
      m_JsonServiceAccountCredentials = jsonServiceAccountCredentials
      this
    }

    @throws[BuildException]
    def build: IServiceAccountCredentials = new ServiceAccountCredentials(getAccountId, getPrivateKey)

    @throws[BuildException]
    private def getAccountId: String = {
      val accountId = m_JsonServiceAccountCredentials.getClientEmail
      if (accountId == null) throw new BuildException("The client_email is missing ")
      accountId
    }

    @throws[BuildException]
    private def getPrivateKey: PrivateKey = {
      val privateKey = m_JsonServiceAccountCredentials.getPrivateKey
      if (!Strings.isEmpty(privateKey)) {
        val pemReader = new PemReader(new StringReader(privateKey))
        try {
          val section = pemReader.readNextSection
          val keySpec = new PKCS8EncodedKeySpec(section.getBase64DecodedBytes)
          return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        } catch {
          case e: Any => throw new BuildException("The supplied credentials are not valid!", e)
        }
      }
      throw new BuildException("The private key within the credentials is missing!")
    }
  }

}

case class ServiceAccountCredentials private(accountId: String, privateKey: PrivateKey)
  extends IServiceAccountCredentials