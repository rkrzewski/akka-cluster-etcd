package pl.caltha.akka.cluster.multijvm

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import com.typesafe.config.ConfigFactory
import pl.caltha.akka.cluster.{ClusterDiscovery, ClusterDiscoverySettings}
import pl.caltha.akka.etcd.EtcdClient

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random


final case class PrimarySeedElectionMultiNodeConfig() extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  commonConfig(ConfigFactory.parseString(
    s"""
       |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
       |akka.cluster.discovery.etcd.timeouts.etcdRetry = 500 ms
       |akka.loglevel = INFO
   """.stripMargin))
}

class PrimarySeedElectionMultiJvmNode1 extends PrimarySeedElectionSpec
class PrimarySeedElectionMultiJvmNode2 extends PrimarySeedElectionSpec
class PrimarySeedElectionMultiJvmNode3 extends PrimarySeedElectionSpec

abstract class PrimarySeedElectionSpec(multiNodeConfig: PrimarySeedElectionMultiNodeConfig)
  extends MultiNodeSpec(multiNodeConfig) with ClusterDiscoverySpec {

  def this() = this(PrimarySeedElectionMultiNodeConfig())

  override def beforeAll() = {
    super.beforeAll()

    val discoverySettings = ClusterDiscoverySettings.load(system.settings.config)
    val httpClientSettings = ClientConnectionSettings(system)
      .withConnectingTimeout(discoverySettings.etcdConnectionTimeout)
      .withIdleTimeout(discoverySettings.etcdRequestTimeout)
    val etcd = EtcdClient(discoverySettings.etcdHost, discoverySettings.etcdPort, Some(httpClientSettings))
    Await.ready(etcd.delete("/akka", recursive = true), 3.seconds)
  }

  "ClusterDiscoveryExtension" should "bootstrap a cluster" in {
    Cluster(system).subscribe(testActor, classOf[MemberUp])
    expectMsgClass(classOf[CurrentClusterState])
    Thread.sleep(Random.nextInt(1000)) //add some randomenss to when the joins happen
    ClusterDiscovery(system).start()

    expectMsgClass(10.seconds, classOf[MemberUp])
    expectMsgClass(10.seconds, classOf[MemberUp])
    expectMsgClass(10.seconds, classOf[MemberUp])

    enterBarrier("done")
  }
}
