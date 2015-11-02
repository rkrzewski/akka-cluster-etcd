package pl.caltha.akka.cluster.monitor

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.event.LoggingAdapter

abstract class NodeBehavior(actorSystem: ActorSystem, log: LoggingAdapter) {

  actorSystem.actorOf(Props(classOf[ShutdownCommandHandler]), "shutdown")

  Cluster(actorSystem).registerOnMemberRemoved {
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
}
