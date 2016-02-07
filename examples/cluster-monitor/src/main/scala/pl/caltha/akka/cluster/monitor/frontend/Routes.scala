package pl.caltha.akka.cluster.monitor.frontend

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.NotUsed
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.SourceShape
import akka.stream.scaladsl._

import spray.json._

import pl.caltha.akka.cluster.monitor.ShutdownCommand

trait Routes {

  implicit def executionContext: ExecutionContext

  implicit def materializer: Materializer

  def cluster: Cluster

  val MaxEventsBacklog: Int = 100

  def jsonDecoder[T: JsonReader]: Flow[Message, T, NotUsed] =
    Flow[Message].mapAsync(1)(_ match {
      case tm: TextMessage ⇒
        tm.textStream.runFold("")(_ + _).map(Some(_))
      case bm: BinaryMessage ⇒
        bm.dataStream.runWith(Sink.ignore)
        Future.successful(None)
    }).collect {
      case Some(s) ⇒ s
    }.map(s ⇒ s.parseJson.convertTo[T])

  def jsonEncoder[T: JsonWriter]: Flow[T, Message, NotUsed] =
    Flow[T].map(e ⇒ TextMessage.Strict(e.toJson.compactPrint))

  import JsonProtocol._

  val welcomeSource: Source[Message, NotUsed] = Source.single(WelcomeMessage(cluster.selfAddress)).via(jsonEncoder)

  val eventsSource: Source[Message, Any] = Source.actorPublisher[ClusterDomainEvent](ClusterEventPublisher.props(MaxEventsBacklog)).via(jsonEncoder)

  val keepaliveSource: Source[Message, Any] = Source.tick(30.seconds, 30.seconds, KeepaliveMessage).via(jsonEncoder)

  val wsSource: akka.stream.scaladsl.Source[Message, NotUsed] = Source.fromGraph(
    GraphDSL.create() {
      implicit builder ⇒
        import GraphDSL.Implicits._
        val welcomeAndEvents = builder.add(welcomeSource.concat(eventsSource))
        val keepalive = builder.add(keepaliveSource)
        val merge = builder.add(Merge[Message](2))
        welcomeAndEvents ~> merge.in(0)
        keepalive ~> merge.in(1)
        SourceShape(merge.out)
    })

  val wsSink: Sink[Message, NotUsed] = jsonDecoder[ShutdownCommand].to(Sink.actorSubscriber(ShutdownCommandForwarder.props))

  private def wsHandler: HttpRequest ⇒ HttpResponse = {
    case req: HttpRequest ⇒ req.header[UpgradeToWebSocket] match {
      case Some(upgrade) ⇒
        upgrade.handleMessagesWithSinkSource(wsSink, wsSource)
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
