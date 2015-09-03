package pl.caltha.akka.cluster

import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.cluster.Cluster
import akka.http.ClientConnectionSettings
import pl.caltha.akka.etcd.EtcdClient

class ClusterDiscoveryImpl(extendedSystem: ExtendedActorSystem) extends Extension {

  private implicit val system = extendedSystem

  val discoverySettings = ClusterDiscoverySettings.load(system.settings.config)

  val httpClientSettings = ClientConnectionSettings(system).copy(
    connectingTimeout = discoverySettings.etcdConnectionTimeout,
    idleTimeout = discoverySettings.etcdRequestTimeout)

  private val etcd = EtcdClient(discoverySettings.etcdHost, discoverySettings.etcdPort, Some(httpClientSettings))

  private val cluster = Cluster(system)

}