package pl.caltha.akka.etcd

import java.net.URLEncoder

import scala.collection.immutable.Traversable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.ClientConnectionSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri._
import akka.io.Inet.SocketOption
import akka.stream.ActorFlowMaterializer
import akka.stream.FlowMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString

import spray.json._

import pl.caltha.akka.http.HttpRedirects

class EtcdClient(host: String, port: Int = 4001,
    socketOptions: Traversable[SocketOption] = Nil,
    httpClientSettings: Option[ClientConnectionSettings] = None)(implicit system: ActorSystem) {

  def get(key: String, recursive: Option[Boolean] = None, sorted: Option[Boolean] = None): Future[EtcdResponse] =
    run(GET, key, "recursive" -> recursive, "sorted" -> sorted)

  def wait(key: String, waitIndex: Option[Int] = None, recursive: Option[Boolean] = None,
    sorted: Option[Boolean] = None, quorum: Option[Boolean] = None): Future[EtcdResponse] =
    run(GET, key, "wait" -> Some(true), "waitIndex" -> waitIndex,
      "recursive" -> recursive, "sorted" -> sorted, "quorum" -> quorum)

  def set(key: String, value: String, ttl: Option[Int] = None): Future[EtcdResponse] =
    run(PUT, key, "value" -> Some(value), "ttl" -> ttl)

  def compareAndSet(key: String, value: String, ttl: Option[Int] = None,
    prevValue: Option[String] = None, prevIndex: Option[Int] = None,
    prevExist: Option[Boolean] = None): Future[EtcdResponse] =
    run(PUT, key, "value" -> Some(value), "ttl" -> ttl,
      "prevValue" -> prevValue, "prevIndex" -> prevIndex, "prevExist" -> prevExist)

  def create(parentKey: String, value: String): Future[EtcdResponse] =
    run(POST, parentKey, "value" -> Some(value))

  def createDir(key: String, ttl: Option[Int] = None): Future[EtcdResponse] =
    run(PUT, key, "dir" -> Some(true), "ttl" -> ttl)

  def delete(key: String, recursive: Boolean = false): Future[EtcdResponse] =
    run(DELETE, key, "recursive" -> Some(recursive))

  def compareAndDelete(key: String, prevValue: Option[String] = None, prevIndex: Option[Int] = None): Future[EtcdResponse] =
    run(DELETE, key, "prevValue" -> prevValue, "prevIndex" -> prevIndex)

  def watch(key: String, waitIndex: Option[Int] = None, recursive: Option[Boolean] = None,
    quorum: Option[Boolean] = None)(implicit executionContext: ExecutionContext): Source[EtcdResponse, Unit] = {
    case class WatchRequest(key: String, waitIndex: Option[Int], recursive: Option[Boolean],
      quorum: Option[Boolean])
    val init = WatchRequest(key, waitIndex, recursive, quorum)
    Source[EtcdResponse]() { implicit b =>
      import FlowGraph.Implicits._

      val initReq = b.add(Source.single(init))
      val reqMerge = b.add(Merge[WatchRequest](2))
      val runWait = b.add(Flow[WatchRequest].mapAsync(1, req => {
        this.wait(req.key, req.waitIndex, req.recursive, req.quorum).map { resp =>
          (req.copy(waitIndex = Some(resp.node.modifiedIndex + 1)), resp)
        }
      }))
      val respUnzip = b.add(Unzip[WatchRequest, EtcdResponse]())

      initReq ~> reqMerge.in(0)
      reqMerge ~> runWait
      runWait ~> respUnzip.in
      respUnzip.out0 ~> reqMerge.in(1)
      respUnzip.out1
    }
  }

  // ---------------------------------------------------------------------------------------------  

  private implicit val executionContext = system.dispatcher

  private implicit val flowMaterializer: FlowMaterializer = ActorFlowMaterializer()

  private val client =
    Http(system).outgoingConnection(host, port, options = socketOptions, settings = httpClientSettings.getOrElse(ClientConnectionSettings(system)))

  private val redirectHandlingClient = HttpRedirects(client, 3)

  private val decode = Flow[HttpResponse].mapAsync(1, response => {
    response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).
      map(_.utf8String).map { body =>
        import EtcdJsonProtocol._
        if (response.status.isSuccess) body.parseJson.convertTo[EtcdResponse]
        else throw EtcdException(body.parseJson.convertTo[EtcdError])
      }
  })

  private def run(req: HttpRequest): Future[EtcdResponse] =
    Source.single(req).via(redirectHandlingClient).via(decode).runWith(Sink.head)

  private def mkParams(params: Seq[(String, Option[Any])]) =
    params.map { case (k, ov) => ov.map(v => k -> v.toString()) }.
      collect { case Some((k, v)) => (k, v) }

  private def mkQuery(params: Seq[(String, Option[Any])]) = {
    Query(mkParams(params).toMap)
  }

  private def enc(s: String) = URLEncoder.encode(s, "UTF-8")

  private def mkEntity(params: Seq[(String, Option[Any])]) = {
    val present = mkParams(params).map { case (k, v) => s"${enc(k)}=${enc(v)}" }
    HttpEntity(ContentType(`application/x-www-form-urlencoded`), present.mkString("&"))
  }

  private val apiV2 = Path / "v2" / "keys"

  private def run(method: HttpMethod, key: String, params: (String, Option[Any])*): Future[EtcdResponse] =
    run(if (method == GET || method == DELETE) {
      HttpRequest(method, Uri(path = apiV2 / key, query = mkQuery(params.toSeq)))
    } else {
      HttpRequest(method, Uri(path = apiV2 / key), entity = mkEntity(params.toSeq))
    })
}