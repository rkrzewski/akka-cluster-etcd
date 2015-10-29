package pl.caltha.akka.cluster.monitor

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
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

  context.system.actorOf(Props(classOf[ShutdownCommandHandler]), "shutdown")

  cluster.registerOnMemberRemoved {
    log.error("Node was removed from cluster, shutting down")
    // exit JVM when ActorSystem has been terminated
    actorSystem.registerOnTermination(System.exit(0))
    // shut down ActorSystem
    actorSystem.terminate()

    // In case ActorSystem shutdown takes longer than 10 seconds,
    // exit the JVM forcefully anyway.
    // We must spawn a separate thread to not block current thread,
    // since that would have blocked the shutdown of the ActorSystem.
    new Thread {
      override def run(): Unit = {
        if (Try(Await.ready(actorSystem.whenTerminated, 10.seconds)).isFailure)
          System.exit(-1)
      }
    }.start()
  }

  def receive = Actor.ignoringBehavior

}
