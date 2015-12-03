package pl.caltha.akka.cluster.monitor.frontend

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.HttpEntity.apply
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCode.int2StatusCode
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.UpgradeToWebsocket
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

import spray.json._

import pl.caltha.akka.cluster.monitor.ShutdownCommand

trait Routes {

  implicit def executionContext: ExecutionContext

  implicit def materializer: Materializer

  def cluster: Cluster

  val MaxEventsBacklog = 100

  def jsonDecoder[T: JsonReader]: Flow[Message, T, Unit] =
    Flow[Message].mapAsync(1)(_ match {
      case tm: TextMessage ⇒
        tm.textStream.runFold("")(_ + _).map(Some(_))
      case bm: BinaryMessage ⇒
        bm.dataStream.runWith(Sink.ignore)
        Future.successful(None)
    }).collect {
      case Some(s) ⇒ s
    }.map(s ⇒ s.parseJson.convertTo[T])

  def jsonEncoder[T: JsonWriter]: Flow[T, Message, Unit] =
    Flow[T].map(e ⇒ TextMessage.Strict(e.toJson.compactPrint))

  private def wsHandler: HttpRequest ⇒ HttpResponse = {
    case req: HttpRequest ⇒ req.header[UpgradeToWebsocket] match {
      case Some(upgrade) ⇒
        import JsonProtocol._
        val wsSink = jsonDecoder[ShutdownCommand].to(Sink.actorSubscriber(ShutdownCommandForwarder.props))
        val welcomeSource = Source.single(WelcomeMessage(cluster.selfAddress)).via(jsonEncoder)
        val eventsSource = Source.actorPublisher[ClusterDomainEvent](ClusterEventPublisher.props(MaxEventsBacklog)).
          via(jsonEncoder)
        upgrade.handleMessagesWithSinkSource(wsSink, welcomeSource.concat(eventsSource))
      case None ⇒ HttpResponse(400, entity = "Missing Upgrade header")
    }
  }

  def routes: Route = path("health") {
    get {
      complete {
        "OK"
      }
    }
  } ~ path("ws") {
    handleWith(wsHandler)
  } ~ path("") {
    getFromResource("public/index.html")
  } ~ getFromResourceDirectory("public")

}
