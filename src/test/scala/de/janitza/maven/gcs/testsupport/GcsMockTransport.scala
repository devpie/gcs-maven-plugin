package de.janitza.maven.gcs.testsupport

import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.testing.http.{MockHttpTransport, MockLowLevelHttpRequest, MockLowLevelHttpResponse}

import scala.collection.mutable

/** One request the service sent, with the body as MockLowLevelHttpRequest hands it over. */
final case class RecordedRequest(method: String, url: String, body: String)

/**
  * Answers the four kinds of request a GoogleCloudStorageService makes and records
  * every one of them, so that a test can assert on what actually went out.
  *
  * The service reaches the network in its constructor, so there is no seam inside
  * the class. There is one at the boundary instead: GCSConfig carries the
  * HttpTransport, which makes this the real HTTP edge rather than a stubbed-out
  * subject under test.
  *
  * @param bucketDefaultAcl what buckets.get reports as defaultObjectAcl, as JSON array
  *                         entries. Empty means the bucket carries no default acl at all.
  * @param insertResponses  answers for the resumable initiation, in order. Once the
  *                         queue runs dry every further attempt succeeds.
  */
final class GcsMockTransport(
  bucketName: String,
  bucketDefaultAcl: Seq[String] = Seq(GcsMockTransport.ownerAclEntry),
  insertResponses: Seq[MockLowLevelHttpResponse] = Seq.empty
) extends MockHttpTransport {

  import GcsMockTransport._

  private val allRequests = mutable.Buffer.empty[RecordedRequest]
  private val queuedInsertResponses = mutable.Queue.from(insertResponses)

  /** The resumable initiation requests, which carry the object metadata. */
  def initiationRequests: Seq[RecordedRequest] = allRequests.toSeq.filter(_.url.contains(UploadPath))

  def requestCount: Int = allRequests.size

  override def buildRequest(method: String, url: String): MockLowLevelHttpRequest =
    new MockLowLevelHttpRequest(url) {
      override def execute(): LowLevelHttpResponse = {
        allRequests += RecordedRequest(method, url, getContentAsString)
        route(url)
      }
    }

  private def route(url: String): MockLowLevelHttpResponse =
    if (url.startsWith(UploadSessionUrl)) json(s"""{"name":"uploaded","bucket":"$bucketName"}""")
    else if (url.contains(TokenHost)) json("""{"access_token":"test-token","expires_in":3600,"token_type":"Bearer"}""")
    else if (url.contains(UploadPath)) nextInsertResponse
    else if (url.contains(s"/storage/v1/b/$bucketName")) json(bucketBody)
    else throw new AssertionError(s"unrouted request: $url")

  private def nextInsertResponse: MockLowLevelHttpResponse =
    if (queuedInsertResponses.isEmpty) uploadAccepted else queuedInsertResponses.dequeue()

  private def bucketBody: String = {
    val acl = if (bucketDefaultAcl.isEmpty) "" else s""","defaultObjectAcl":[${bucketDefaultAcl.mkString(",")}]"""
    s"""{"name":"$bucketName","location":"EU"$acl}"""
  }
}

object GcsMockTransport {
  val UploadSessionUrl = "https://storage.googleapis.com/upload/session/1"
  val DefaultAclEntity = "project-owners-42"
  val AllUsers = "allUsers"

  def ownerAclEntry: String = s"""{"entity":"$DefaultAclEntity","role":"OWNER"}"""

  /** A bucket that already grants public read, which addPublicReadAccess must not duplicate. */
  def allUsersReaderAclEntry: String = s"""{"entity":"$AllUsers","role":"READER"}"""
  private val UploadPath = "/upload/storage/v1/"
  private val TokenHost = "oauth2.googleapis.com/token"

  def json(body: String, statusCode: Int = 200): MockLowLevelHttpResponse =
    new MockLowLevelHttpResponse()
      .setStatusCode(statusCode)
      .setContentType("application/json; charset=UTF-8")
      .setContent(body)

  /** Accepts the resumable initiation and hands out the session URL for the media PUT. */
  def uploadAccepted: MockLowLevelHttpResponse = json("{}").addHeader("Location", UploadSessionUrl)

  def serviceUnavailable: MockLowLevelHttpResponse =
    json("""{"error":{"code":503,"message":"Backend Error"}}""", 503)

}
