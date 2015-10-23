package pl.caltha.akka.cluster.monitor

import akka.actor.Actor
import akka.http.ClientConnectionSettings
import pl.caltha.akka.cluster.ClusterDiscoverySettings
import pl.caltha.akka.etcd.EtcdClient
import pl.caltha.akka.cluster.ClusterDiscovery

class Main extends Actor {

  val discoverySettings = ClusterDiscoverySettings.load(context.system.settings.config)
  val httpClientSettings = ClientConnectionSettings(context.system).copy(
    connectingTimeout = discoverySettings.etcdConnectionTimeout,
    idleTimeout = discoverySettings.etcdRequestTimeout)
  val etcd = EtcdClient(discoverySettings.etcdHost, discoverySettings.etcdPort,
    Some(httpClientSettings))(context.system)
  ClusterDiscovery(context.system).start()

  def receive = Actor.ignoringBehavior

}
