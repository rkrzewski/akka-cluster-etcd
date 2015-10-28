package pl.caltha.akka.cluster.monitor

import akka.stream.Materializer
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.ws.UpgradeToWebsocket
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.Source
import akka.stream.OverflowStrategy
import akka.cluster.ClusterEvent.ClusterDomainEvent
import spray.json._
import JsonProtocol._
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.scaladsl.Sink

trait Routes {

  implicit def executionContext: ExecutionContext

  implicit def materializer: Materializer

  val MaxEventsBacklog = 100

  private def wsHandler: HttpRequest ⇒ HttpResponse = {
    case req: HttpRequest ⇒ req.header[UpgradeToWebsocket] match {
      case Some(upgrade) ⇒
        val wsSink = Sink.ignore
        val wsSourceProps = ClusterEventPublisher.props(MaxEventsBacklog)
        val wsSource = Source.actorPublisher[ClusterDomainEvent](wsSourceProps).
          map(e ⇒ e.toJson).map(json ⇒ TextMessage.Strict(json.compactPrint))
        upgrade.handleMessagesWithSinkSource(wsSink, wsSource)
      case None ⇒ HttpResponse(400, entity = "Missing Upgrade header")
    }
  }

  def routes = path("health") {
    get {
      complete {
        "OK"
      }
    }
  } ~ path("ws") {
    handleWith(wsHandler)
  }

}
