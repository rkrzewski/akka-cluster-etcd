package pl.caltha.akka.cluster

import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.remote.testconductor.RoleName
import akka.testkit._

final case class PrimarySeedElectionMultiNodeConfig() extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  commonConfig(debugConfig(on = false))
}

class PrimarySeedElectionMultiJvmNode1 extends PrimarySeedElectionSpec
class PrimarySeedElectionMultiJvmNode2 extends PrimarySeedElectionSpec
class PrimarySeedElectionMultiJvmNode3 extends PrimarySeedElectionSpec

abstract class PrimarySeedElectionSpec(multiNodeConfig: PrimarySeedElectionMultiNodeConfig)
    extends MultiNodeSpec(multiNodeConfig) with ClusterDiscoverySpec {
  
  def this() = this(PrimarySeedElectionMultiNodeConfig())
  
  import multiNodeConfig._

}