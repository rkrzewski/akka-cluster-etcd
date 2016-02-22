package pl.caltha.akka.etcd

import java.net.URLEncoder

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import akka.stream.SourceShape
import akka.stream.scaladsl._
import akka.util.ByteString

import spray.json._

import pl.caltha.akka.streams.FlowBreaker

/**
  * `etcd` client implementation.
  */
private[etcd] class EtcdClientImpl(host: String, port: Int = 4001,
                                   httpClientSettings: Option[ClientConnectionSettings] = None)(implicit system: ActorSystem) extends EtcdClient {

  private def bool(name: String, value: Boolean): Option[(String, String)] =
    if (value) Some(name → value.toString) else None

  private def opt[T](name: String, option: Option[T]): Option[(String, String)] =
    option map { case value ⇒ name → value.toString }

  def get(key: String, recursive: Boolean, sorted: Boolean): Future[EtcdResponse] =
    run(GET, key, bool("recursive", recursive), bool("sorted", sorted))

  def wait(key: String, waitIndex: Option[Int] = None, recursive: Boolean,
           sorted: Boolean, quorum: Boolean): Future[EtcdResponse] =
    run(GET, key, Some("wait" → "true"), opt("waitIndex", waitIndex),
      bool("recursive", recursive), bool("sorted", sorted), bool("quorum", quorum))

  def set(key: String, value: String, ttl: Option[Int] = None): Future[EtcdResponse] =
    run(PUT, key, Some("value" → value), opt("ttl", ttl))

  def compareAndSet(key: String, value: String, ttl: Option[Int] = None, prevValue: Option[String] = None,
                    prevIndex: Option[Int] = None, prevExist: Option[Boolean] = None): Future[EtcdResponse] =
    run(PUT, key, Some("value" → value), opt("ttl", ttl), opt("prevValue", prevValue),
      opt("prevIndex", prevIndex), opt("prevExist", prevExist))

  def clearTTL(key: String): Future[EtcdResponse] =
    run(PUT, key, Some("ttl" → ""), Some("prevExists" → "true"))

  def create(parentKey: String, value: String): Future[EtcdResponse] =
    run(POST, parentKey, Some("value" → value))

  def createDir(key: String, ttl: Option[Int] = None): Future[EtcdResponse] =
    run(PUT, key, Some("dir" → "true"), opt("ttl", ttl))

  def delete(key: String, recursive: Boolean = false): Future[EtcdResponse] =
    run(DELETE, key, bool("recursive", recursive))

  def compareAndDelete(key: String, prevValue: Option[String] = None, prevIndex: Option[Int] = None): Future[EtcdResponse] =
    run(DELETE, key, opt("prevValue", prevValue), opt("prevIndex", prevIndex))

  private implicit val executionContext = system.dispatcher

  def watch(key: String, waitIndex: Option[Int] = None, recursive: Boolean,
            quorum: Boolean): Source[EtcdResponse, Cancellable] = {

    case class WatchRequest(key: String, waitIndex: Option[Int], recursive: Boolean, quorum: Boolean)
    val init = WatchRequest(key, waitIndex, recursive, quorum)

    Source.fromGraph(
      GraphDSL.create[SourceShape[EtcdResponse]]() { implicit b ⇒
        import GraphDSL.Implicits._

        val initReq = b.add(Source.single(init))
        val reqMerge = b.add(Merge[WatchRequest](2))
        val runWait = b.add(Flow[WatchRequest].mapAsync(1)(req ⇒ {
          this.wait(req.key, req.waitIndex, req.recursive, req.quorum).map { resp ⇒
            (req.copy(waitIndex = Some(resp.node.modifiedIndex + 1)), resp)
          }
        }))
        val respUnzip = b.add(Unzip[WatchRequest, EtcdResponse]())

        // @formatter:off format: OFF
        initReq                   ~> reqMerge.in(0)
        respUnzip.out0            ~> reqMerge.in(1)
                                     reqMerge       ~> runWait ~> respUnzip.in
        // @formatter:on format: ON

        SourceShape(respUnzip.out1)
      }).named("watch").viaMat(FlowBreaker[EtcdResponse])(Keep.right)
  }

  // ---------------------------------------------------------------------------------------------

  private implicit val Materializer: Materializer = ActorMaterializer()

  private val client =
    Http(system).outgoingConnection(host, port, settings = httpClientSettings.getOrElse(ClientConnectionSettings(system)))

  private val decode = Flow[HttpResponse].mapAsync(1)(response ⇒ {
    response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).
      map(_.utf8String).map { body ⇒
        import EtcdJsonProtocol._
        if (response.status.isSuccess) body.parseJson.convertTo[EtcdResponse]
        else throw EtcdException(body.parseJson.convertTo[EtcdError])
      }
  })

  private def run(req: HttpRequest): Future[EtcdResponse] =
    Source.single(req).via(client).via(decode).runWith(Sink.head)

  private def mkParams(params: Seq[Option[(String, String)]]) =
    params.collect { case Some((k, v)) ⇒ (k, v) }

  private def mkQuery(params: Seq[Option[(String, String)]]) = {
    Query(mkParams(params).toMap)
  }

  private def enc(s: String) = URLEncoder.encode(s, "UTF-8")

  private def mkEntity(params: Seq[Option[(String, String)]]) = {
    val present = mkParams(params).map { case (k, v) ⇒ s"${enc(k)}=${enc(v)}" }
    HttpEntity(ContentType(`application/x-www-form-urlencoded`, `UTF-8`), present.mkString("&"))
  }

  private val apiV2 = Path / "v2" / "keys"

  def keyPath(key: String) = key.split("/").
    filter(segment ⇒ segment.length() > 0).
    foldLeft(apiV2) {
      case (path, segment) ⇒ path / segment
    }

  private def run(method: HttpMethod, key: String, params: Option[(String, String)]*): Future[EtcdResponse] =
    run(if (method == GET || method == DELETE) {
      HttpRequest(method, Uri(path = keyPath(key)).withQuery(mkQuery(params.toSeq)))
    } else {
      HttpRequest(method, Uri(path = keyPath(key)), entity = mkEntity(params.toSeq))
    })
}
