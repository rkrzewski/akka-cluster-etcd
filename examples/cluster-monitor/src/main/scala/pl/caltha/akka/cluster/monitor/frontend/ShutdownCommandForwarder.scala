package pl.caltha.akka.cluster.monitor.frontend

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.RootActorPath
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage._
import akka.stream.actor.OneByOneRequestStrategy
import akka.actor.ActorSelection.toScala

import pl.caltha.akka.cluster.monitor.ShutdownCommand

class ShutdownCommandForwarder extends ActorSubscriber with ActorLogging {

  implicit val executionContext = context.dispatcher

  implicit val materializer = ActorMaterializer()

  def requestStrategy = OneByOneRequestStrategy

  def receive: Actor.Receive = {
    case OnNext(cmd: ShutdownCommand) ⇒
      context.actorSelection(RootActorPath(cmd.target) / "user" / "shutdown") ! cmd
    case OnError(e) ⇒
      log.error(e, "Incoming stream terminated with error")
      context.stop(self)
    case OnComplete ⇒
      context.stop(self)
  }
}

object ShutdownCommandForwarder {
  def props = Props(classOf[ShutdownCommandForwarder])
}
