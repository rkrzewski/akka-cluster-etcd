package pl.caltha.akka.etcd

import java.net.URLEncoder

import scala.collection.immutable.Traversable
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.engine.client.ClientConnectionSettings
import akka.http.model.ContentType
import akka.http.model.HttpEntity
import akka.http.model.HttpMethod
import akka.http.model.HttpMethods._
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.MediaTypes._
import akka.http.model.Uri
import akka.http.model.Uri._
import akka.io.Inet.SocketOption
import akka.stream.ActorFlowMaterializer
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import spray.json.pimpString

class EtcdClient(host: String, port: Int = 4001,
    socketOptions: Traversable[SocketOption] = Nil,
    httpClientSettings: Option[ClientConnectionSettings] = None,
    etcdOptions: Traversable[EtcdOption] = Nil)(implicit system: ActorSystem) {

  private val keys = Path / "v2" / "keys"

  def get(key: String, recursive: Option[Boolean] = None, sorted: Option[Boolean] = None): Future[EtcdResponse] =
    run(GET, key, "recursive" -> recursive, "sorted" -> sorted)

  def wait(key: String, waitKey: Option[Int] = None, recursive: Option[Boolean] = None, sorted: Option[Boolean] = None): Future[EtcdResponse] =
    run(GET, key, "wait" -> Some(true), "waitKey" -> waitKey,
      "recursive" -> recursive, "sorted" -> Some(sorted))

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

  private implicit val executionContext = system.dispatcher

  private implicit val flowMaterializer: FlowMaterializer = ActorFlowMaterializer()

  private val clientFlow =
    Http(system).outgoingConnection(host, port, options = socketOptions, settings = httpClientSettings)

  private val decodeFlow = Flow[HttpResponse].mapAsync { response =>
    response.entity.dataBytes.runFold(ByteString.empty)((u, v) => u.concat(v)).
      map(_.utf8String).map { body =>
        import EtcdJsonProtocol._
        if (response.status.isSuccess) body.parseJson.convertTo[EtcdResponse]
        else throw EtcdException(body.parseJson.convertTo[EtcdError])
      }
  }

  private def run(req: HttpRequest): Future[EtcdResponse] =
    Source.single(req).via(clientFlow).via(decodeFlow).runWith(Sink.head)

  private def mkQuery(params: (String, Option[Any])*) = {
    val present = params.map { case (k, ov) => ov.map(v => k -> v.toString()) }.
      collect { case Some((k, v)) => (k, v) }
    Query(present.toMap)
  }

  private def enc(s: String) = URLEncoder.encode(s, "UTF-8")

  private def mkEntity(params: (String, Option[Any])*) = {
    val present = params.map { case (k, ov) => ov.map(v => k -> v.toString()) }.
      collect { case Some((k, v)) => s"${enc(k)}=${enc(v)}" }
    HttpEntity(ContentType(`application/x-www-form-urlencoded`), present.mkString("&"))
  }

  private val apiV2 = Path / "v2" / "keys"

  private def run(method: HttpMethod, key: String, params: (String, Option[Any])*): Future[EtcdResponse] =
    run(method match {
      case GET => HttpRequest(method, Uri(path = apiV2 / key, query = mkQuery(params: _*)))
      case DELETE => HttpRequest(method, Uri(path = apiV2 / key, query = mkQuery(params: _*)))
      case PUT => HttpRequest(method, Uri(path = apiV2 / key), entity = mkEntity(params: _*))
      case POST => HttpRequest(method, Uri(path = apiV2 / key), entity = mkEntity(params: _*))
    })

}