package de.janitza.maven.gcs.api

import java.io.IOException
import java.security.PrivateKey

import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory

trait IGCSConfig {
  def jsonFactory: JsonFactory

  def httpTransport: HttpTransport

  def scopes: Seq[String]

  def serviceAccountCredentials: IServiceAccountCredentials

  def gcsApplicationName: String

  def bucketName: String
}

trait IJsonCredentialsLoader {
  @throws[BuildException]
  def load(jsonSecretsFile: String): IServiceAccountCredentials
}

trait IJsonServiceAccountCredentials {
  def getClientEmail: String

  def getPrivateKey: String
}

trait IServiceAccountCredentials {
  def accountId: String

  def privateKey: PrivateKey
}