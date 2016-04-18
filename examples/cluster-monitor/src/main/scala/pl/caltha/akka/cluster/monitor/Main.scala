package pl.caltha.akka.cluster.monitor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.Cluster

import pl.caltha.akka.cluster.ClusterDiscovery
import pl.caltha.akka.cluster.monitor.backend.BackendBehavior
import pl.caltha.akka.cluster.monitor.frontend.FrontendBehavior
import me.maciejb.etcd.client.EtcdClient

class Main extends Actor with ActorLogging {

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
