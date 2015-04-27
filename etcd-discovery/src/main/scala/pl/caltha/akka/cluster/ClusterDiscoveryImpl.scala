package pl.caltha.akka.cluster

import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.cluster.Cluster
import pl.caltha.akka.etcd.EtcdClient

class ClusterDiscoveryImpl(extendedSystem: ExtendedActorSystem) extends Extension {
  
  private implicit val system = extendedSystem
  
  val etcdHost = system.settings.config.getString("akka.cluster.discovery.etcd.host")
  val etcdPort = system.settings.config.getInt("akka.cluster.discovery.etcd.port")
  val etcdPath = system.settings.config.getString("akka.cluster.discovery.etcd.path")
  
  private val etcd = new EtcdClient(etcdHost, etcdPort)
  
  private val cluster = Cluster(system)
  
}