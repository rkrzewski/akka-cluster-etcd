package pl.caltha.akka.cluster.multijvm

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.FlatSpecLike
import org.scalatest.Matchers

import akka.remote.testkit.MultiNodeSpec
import akka.remote.testkit.MultiNodeSpecCallbacks

trait ClusterDiscoverySpec extends MultiNodeSpecCallbacks with FlatSpecLike with Matchers with BeforeAndAfterAll {
  self: MultiNodeSpec ⇒

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  override def initialParticipants = roles.size
}