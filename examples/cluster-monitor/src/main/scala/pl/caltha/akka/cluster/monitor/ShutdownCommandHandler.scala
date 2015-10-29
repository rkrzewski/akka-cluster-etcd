package pl.caltha.akka.cluster.monitor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.cluster.Cluster

class ShutdownCommandHandler extends Actor with ActorLogging {
  val selfAddress = Cluster(context.system).selfAddress
  def receive = {
    case ShutdownCommand(address, _) if address != selfAddress ⇒
      log.error("received ShutdownCommand for node {}, something is amiss", address)
    case ShutdownCommand(_, immediate) if !immediate ⇒
      log.error("received shutdown command")
    // TODO
    case ShutdownCommand(_, _) ⇒
      log.error("received immediate shutdown command")
    // TODO
  }
}

object ShutdownCommandHandler {
  def props = Props(classOf[ShutdownCommandHandler])
}
