package pl.caltha.akka.cluster

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider

object ClusterDiscovery extends ExtensionId[ClusterDiscoveryImpl] with ExtensionIdProvider {

  override def lookup = ClusterDiscovery

  override def createExtension(system: ExtendedActorSystem) = new ClusterDiscoveryImpl(system)

  override def get(system: ActorSystem): ClusterDiscoveryImpl = super.get(system)

}