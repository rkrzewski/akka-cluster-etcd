package pl.caltha.akka.cluster.monitor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.cluster.Cluster

class ShutdownCommandHandler extends Actor with ActorLogging {
  val cluster = Cluster(context.system)
  val selfAddress = cluster.selfAddress
  def receive = {
    case ShutdownCommand(address, _) if address != selfAddress ⇒
      log.error("received ShutdownCommand for node {}, something is amiss", address)
    case ShutdownCommand(_, immediate) if !immediate ⇒
      log.error("received shutdown command")
      cluster.leave(selfAddress)
    case ShutdownCommand(_, _) ⇒
      log.error("received immediate shutdown command")
      System.exit(1)
  }
}

object ShutdownCommandHandler {
  def props = Props(classOf[ShutdownCommandHandler])
}
