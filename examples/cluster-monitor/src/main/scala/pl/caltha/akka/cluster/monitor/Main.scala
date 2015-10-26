package pl.caltha.akka.cluster.monitor

import scala.util.Failure
import scala.util.Success

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.http.ClientConnectionSettings
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import pl.caltha.akka.cluster.ClusterDiscovery
import pl.caltha.akka.cluster.ClusterDiscoverySettings
import pl.caltha.akka.etcd.EtcdClient

class Main extends Actor with Routes with ActorLogging {

  val discoverySettings = ClusterDiscoverySettings.load(context.system.settings.config)

  val httpClientSettings = ClientConnectionSettings(context.system).copy(
    connectingTimeout = discoverySettings.etcdConnectionTimeout,
    idleTimeout = discoverySettings.etcdRequestTimeout)

  val etcd = EtcdClient(discoverySettings.etcdHost, discoverySettings.etcdPort,
    Some(httpClientSettings))(context.system)

  ClusterDiscovery(context.system).start()

  val cluster = Cluster(context.system)
  cluster.registerOnMemberUp {
    if (cluster.getSelfRoles.contains("frontend")) {
      val binding = Http().bindAndHandle(routes, "0.0.0.0", 8080)
      binding.onComplete {
        case Success(b) ⇒ log.info("Started HTTP server at {}", b.localAddress)
        case Failure(t) ⇒ log.error(t, "Failed to start HTTP server")
      }
    }
  }

  implicit val actorSystem = context.system

  implicit val executionContext = context.dispatcher

  implicit val materializer = ActorMaterializer()

  def receive = Actor.ignoringBehavior

}
