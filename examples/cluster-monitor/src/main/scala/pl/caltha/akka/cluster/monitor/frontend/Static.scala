package pl.caltha.akka.cluster.monitor.frontend

import scala.util.Failure
import scala.util.Success

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.Materializer

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

  def routes: Route = 
    path("") {
      getFromResource("public/index.html")
    } ~
    getFromResourceDirectory("public")
}