package pl.caltha.akka.cluster.monitor.frontend

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.NotUsed
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.AddressFromURIString
import akka.cluster.ClusterEvent._
import akka.cluster.MemberFactory
import akka.cluster.MemberStatus
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import akka.stream.SourceShape
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Zip

import spray.json._

class Static extends Actor with ActorLogging {

  implicit val actorSystem = context.system

  implicit val executionContext = actorSystem.dispatcher

  implicit val materializer: Materializer = ActorMaterializer()

  def receive = Actor.ignoringBehavior

  val binding = Http().bindAndHandle(routes, "0.0.0.0", 8080)
  binding.onComplete {
    case Success(b) ⇒ log.info("Started HTTP server at {}", b.localAddress)
    case Failure(t) ⇒ log.error(t, "Failed to start HTTP server")
  }

  // drain data from any incoming message
  val wsSink: Sink[Message, _] = Flow[Message].mapAsync(1)(_ match {
    case m: TextMessage ⇒
      m.textStream.runWith(Sink.ignore)
    case m: BinaryMessage ⇒
      m.dataStream.runWith(Sink.ignore)
  }).to(Sink.ignore)

  val scenario: Seq[ClusterDomainEvent] = Seq(
    MemberUp(MemberFactory("akka.tcp://Main@172.17.0.3:2552", Set("frontend"), MemberStatus.up)),
    LeaderChanged(Some(AddressFromURIString("akka.tcp://Main@172.17.0.3:2552"))),
    RoleLeaderChanged("frontend", Some(AddressFromURIString("akka.tcp://Main@172.17.0.3:2552"))),
    MemberUp(MemberFactory("akka.tcp://Main@172.17.0.4:2552", Set("backend"), MemberStatus.up)),
    RoleLeaderChanged("backend", Some(AddressFromURIString("akka.tcp://Main@172.17.0.4:2552"))),
    MemberUp(MemberFactory("akka.tcp://Main@172.17.0.5:2552", Set("backend"), MemberStatus.up)),
    UnreachableMember(MemberFactory("akka.tcp://Main@172.17.0.5:2552", Set("backend"), MemberStatus.up)),
    ReachableMember(MemberFactory("akka.tcp://Main@172.17.0.5:2552", Set("backend"), MemberStatus.up)),
    UnreachableMember(MemberFactory("akka.tcp://Main@172.17.0.5:2552", Set("backend"), MemberStatus.up)),
    MemberRemoved(MemberFactory("akka.tcp://Main@172.17.0.5:2552", Set("backend"), MemberStatus.removed), MemberStatus.down))

  def jsonEncoder[T: JsonWriter]: Flow[T, Message, NotUsed] =
    Flow[T].map(e ⇒ TextMessage.Strict(e.toJson.compactPrint))

  // send events from scenario in an infinite loop, once every 5 seconds
  val wsSource: Source[Message, _] = Source.fromGraph(
    GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._
      import JsonProtocol._
      val welcomeSource = Source.single(WelcomeMessage(AddressFromURIString("akka.tcp://Main@172.17.0.3:2552"))).via(jsonEncoder)
      val eventsSource = Source.repeat(()).mapConcat(_ ⇒ scenario.to[collection.immutable.Iterable]).via(jsonEncoder)

      val messages = b.add(welcomeSource.concat(eventsSource))
      val ticks = b.add(Source.tick(0.seconds, 5.seconds, ()))
      val zip = b.add(Zip[Message, Unit])
      val extract = b.add(Flow[(Message, Unit)].map { case (e, _) ⇒ e })

      messages ~> zip.in0
      ticks ~> zip.in1
      zip.out ~> extract
      SourceShape(extract.outlet)
    })

  private def wsHandler: HttpRequest ⇒ HttpResponse = {
    case req: HttpRequest ⇒ req.header[UpgradeToWebSocket] match {
      case Some(upgrade) ⇒
        upgrade.handleMessagesWithSinkSource(wsSink, wsSource)
      case None ⇒ HttpResponse(400, entity = "Missing Upgrade header")
    }
  }

  def routes: Route =
    path("") {
      getFromResource("public/index.html")
    } ~ getFromResourceDirectory("public") ~ path("ws") {
      handleWith(wsHandler)
    }
}
