package pl.caltha.akka.cluster.monitor.backend

import akka.actor.ActorSystem
import akka.event.LoggingAdapter

import pl.caltha.akka.cluster.monitor.NodeBehavior

class BackendBehavior(actorSystem: ActorSystem, log: LoggingAdapter)
  extends NodeBehavior(actorSystem, log)
