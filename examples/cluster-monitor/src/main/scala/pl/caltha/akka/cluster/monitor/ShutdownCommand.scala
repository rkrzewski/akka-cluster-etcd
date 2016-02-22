package pl.caltha.akka.cluster.monitor

import akka.actor.Address

/**
  * @param target the cluster member that should be shut down
  * @param immediate when `true` VM should be terminated forcibly without leaving the cluster and
  *         orderly actor system termination.
  */
case class ShutdownCommand(target: Address, immediate: Boolean)
