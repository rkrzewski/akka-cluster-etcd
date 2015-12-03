package pl.caltha.akka.cluster.monitor.frontend

import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.Materializer

import pl.caltha.akka.cluster.monitor.NodeBehavior

class FrontendBehavior(actorSystem: ActorSystem, log: LoggingAdapter)
    extends NodeBehavior(actorSystem, log) with Routes {

  implicit val implActorSystem = actorSystem

  implicit val executionContext = actorSystem.dispatcher

  implicit val materializer: Materializer = ActorMaterializer()

  def cluster = Cluster(actorSystem)

  val binding = Http().bindAndHandle(routes, "0.0.0.0", 8080)
  binding.onComplete {
    case Success(b) ⇒ log.info("Started HTTP server at {}", b.localAddress)
    case Failure(t) ⇒ log.error(t, "Failed to start HTTP server")
  }

}
