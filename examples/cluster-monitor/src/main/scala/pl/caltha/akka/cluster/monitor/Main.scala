package pl.caltha.akka.cluster.monitor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.http.scaladsl.settings.ClientConnectionSettings

import pl.caltha.akka.cluster.ClusterDiscovery
import pl.caltha.akka.cluster.ClusterDiscoverySettings
import pl.caltha.akka.cluster.monitor.backend.BackendBehavior
import pl.caltha.akka.cluster.monitor.frontend.FrontendBehavior
import pl.caltha.akka.etcd.EtcdClient

class Main extends Actor with ActorLogging {

  val discoverySettings = ClusterDiscoverySettings.load(context.system.settings.config)

  val httpClientSettings = ClientConnectionSettings(context.system)
    .withConnectingTimeout(discoverySettings.etcdConnectionTimeout)
    .withIdleTimeout(discoverySettings.etcdRequestTimeout)

  val etcd = EtcdClient(discoverySettings.etcdHost, discoverySettings.etcdPort,
    Some(httpClientSettings))(context.system)

  ClusterDiscovery(context.system).start()

  var behavior: NodeBehavior = _

  val cluster = Cluster(context.system)

  cluster.registerOnMemberUp {
    behavior = if (cluster.getSelfRoles.contains("frontend"))
      new FrontendBehavior(context.system, log)
    else
      new BackendBehavior(context.system, log)
  }

  def receive = Actor.ignoringBehavior
}
