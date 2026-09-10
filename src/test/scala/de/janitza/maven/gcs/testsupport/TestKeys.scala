package de.janitza.maven.gcs.testsupport

import java.security.{KeyPairGenerator, PrivateKey}
import java.util.Base64

/**
  * One RSA key pair for the whole test run, generated on the fly.
  *
  * A private key checked into the repository would trip every secret scanner and
  * need a standing explanation that it is not real. Generating costs around 50 to
  * 150 ms, which is why this is a lazy val and not a per-test call.
  */
object TestKeys {

  lazy val rsaPrivateKey: PrivateKey = {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    generator.generateKeyPair.getPrivate
  }

  /** getEncoded already yields PKCS#8 DER for JDK RSA keys, which is what PemReader expects. */
  def asPem(key: PrivateKey): String = {
    val body = Base64.getMimeEncoder(64, Array('\n'.toByte)).encodeToString(key.getEncoded)
    s"-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n"
  }
}
