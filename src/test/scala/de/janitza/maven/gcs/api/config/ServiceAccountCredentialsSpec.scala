package de.janitza.maven.gcs.api.config

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.Arrays

import de.janitza.maven.gcs.api.{Error, Success}
import de.janitza.maven.gcs.testsupport.{TempFiles, TestKeys}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import play.api.libs.json.{JsValue, Json}

/**
  * The credentials path decides whether the plugin can authenticate at all, and it
  * is the one place where a wrong answer is a security question rather than a
  * convenience one.
  *
  * The RSA key comes from TestKeys, generated while the test runs.
  */
class ServiceAccountCredentialsSpec extends AnyFreeSpec with BeforeAndAfterAll {

  private val AccountId = "test@example.iam.gserviceaccount.com"

  private var tempDir: Path = _


  override def beforeAll(): Unit = tempDir = TempFiles.directory("gcs-credentials-spec")

  override def afterAll(): Unit = TempFiles.deleteRecursively(tempDir)


  /** Built through play-json, because a PEM block contains newlines that hand-written JSON would break on. */
  private def writeJson(name: String, json: JsValue): String = writeRaw(name, Json.stringify(json))

  private def writeRaw(name: String, content: String): String =
    Files.write(tempDir.resolve(name), content.getBytes(UTF_8)).toString

  private def secretsFile(name: String, accountId: String, privateKey: String): String =
    writeJson(name, Json.obj("client_email" -> accountId, "private_key" -> privateKey))

  "load" - {

    "returns the account and the key from a valid secrets file" in {
      val path = secretsFile("valid.json", AccountId, TestKeys.asPem(TestKeys.rsaPrivateKey))

      ServiceAccountCredentials.load(path) match {
        case Success(credentials) =>
          assert(credentials.accountId == AccountId)
          assert(Arrays.equals(credentials.privateKey.getEncoded, TestKeys.rsaPrivateKey.getEncoded))
        case other => fail(s"expected Success, got $other")
      }
    }

    "ignores the extra fields a real Google secrets file carries" in {
      val path = writeJson("full.json", Json.obj(
        "type" -> "service_account",
        "project_id" -> "some-project",
        "private_key_id" -> "abc123",
        "private_key" -> TestKeys.asPem(TestKeys.rsaPrivateKey),
        "client_email" -> AccountId,
        "client_id" -> "1234567890",
        "token_uri" -> "https://oauth2.googleapis.com/token"
      ))

      assert(ServiceAccountCredentials.load(path).isInstanceOf[Success[_]])
    }

    "reports an Error naming the missing field when client_email is absent" in {
      val path = writeJson("no-email.json", Json.obj("private_key" -> TestKeys.asPem(TestKeys.rsaPrivateKey)))

      ServiceAccountCredentials.load(path) match {
        case Error(message, _) => assert(message.contains("client_email"))
        case other => fail(s"expected an Error, got $other")
      }
    }

    "reports an Error when the private key is empty" in {
      val path = secretsFile("empty-key.json", AccountId, "")

      assert(ServiceAccountCredentials.load(path) == Error("The private key within the credentials is missing!"))
    }

    "reports an Error when the private key is not a PEM block" in {
      val path = secretsFile("garbage-key.json", AccountId, "not a pem at all")

      ServiceAccountCredentials.load(path) match {
        case Error(message, _) => assert(message == "The supplied credentials are not valid!")
        case other => fail(s"expected an Error, got $other")
      }
    }

    // load is declared Result[ServiceAccountCredentials] and its only caller,
    // GCSConfigBuilder.build, handles nothing but a Result. The unreadable inputs below
    // therefore arrive as an Error rather than as an exception. The message names the file,
    // because that is what tells a user about a typo in json-secrets-file.
    "reports an Error naming the file when it does not exist" in {
      val missing = tempDir.resolve("no-such-file.json").toString

      ServiceAccountCredentials.load(missing) match {
        case Error(message, _) => assert(message.contains("no-such-file.json"))
        case other => fail(s"expected an Error, got $other")
      }
    }

    "reports an Error naming the file when it is not valid JSON" in {
      val path = writeRaw("broken.json", "{ this is not json")

      ServiceAccountCredentials.load(path) match {
        case Error(message, _) => assert(message.contains("broken.json"))
        case other => fail(s"expected an Error, got $other")
      }
    }

    "reports an Error naming the file when it is empty" in {
      val path = writeRaw("empty.json", "")

      ServiceAccountCredentials.load(path) match {
        case Error(message, _) => assert(message.contains("empty.json"))
        case other => fail(s"expected an Error, got $other")
      }
    }
  }
}
