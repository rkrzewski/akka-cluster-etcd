package pl.caltha.akka.cluster

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.mockito.Mockito

import org.scalatest.Finders
import org.scalatest.FlatSpecLike
import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar

import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.FSM.CurrentState
import akka.actor.FSM.SubscribeTransitionCallBack
import akka.actor.FSM.Transition
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.stream.StreamTcpException
import akka.testkit.TestFSMRef
import akka.testkit.TestKit
import akka.testkit.TestProbe

import pl.caltha.akka.etcd.EtcdClient
import pl.caltha.akka.etcd.EtcdNode
import pl.caltha.akka.etcd.EtcdResponse
import pl.caltha.akka.etcd.EtcdException
import pl.caltha.akka.etcd.EtcdError
import pl.caltha.akka.etcd.EtcdCommandException

class ClusterDiscoveryActorSpec extends EtcdFSMSpecBase[ClusterDiscoveryActor.State, ClusterDiscoveryActor.Data] {

  import Mockito.{ when, verify }
  import ClusterDiscoveryActor._

  val selfAddress = Address("akka", "testsystem")

  trait Fixture extends FixtureBase {

    val cluster = mock[Cluster]

    when(cluster.selfAddress).thenReturn(selfAddress)

    def init(testSettings: ClusterDiscoverySettings = settings) = {
      val discovery = TestFSMRef(new ClusterDiscoveryActor(etcd, cluster, testSettings))
      discovery ! SubscribeTransitionCallBack(stateProbe.ref)
      expectInitialState(Initial)
      discovery
    }

    def initReq =
      etcd.createDir(settings.etcdPath, None)

    val initSuccessResp = Future.successful(
      EtcdResponse("created",
        EtcdNode(settings.etcdPath, 0, 0, None, None, Some(true), Some(List.empty)),
        None))

    val initNodeExistsResp = Future.failed(
      EtcdException(
        EtcdError(EtcdError.NodeExist, "", "", 0)))

    def electionBidReq =
      etcd.compareAndSet(
        settings.leaderPath,
        selfAddress.toString,
        Some(settings.leaderEntryTTL.toSeconds.asInstanceOf[Int]),
        None,
        None,
        Some(false))

    val electionBidSuccessResp = Future.successful(
      EtcdResponse("created",
        EtcdNode(settings.etcdPath, 0, 0, None, None, Some(true), Some(List.empty)),
        None))

    val eleectionBidFailureResp = Future.failed(
      EtcdException(
        EtcdError(EtcdError.NodeExist, "Node Exists", settings.leaderPath, 100)))

    val electionBidTransientFailureResp = Future.failed(
      new StreamTcpException("Connection failed"))

    def fetchSeedsReq =
      etcd.get(settings.seedsPath, true)

    val noSeedsResp = Future.successful(
      EtcdResponse("get",
        EtcdNode(settings.seedsPath, 0, 0, None, None, Some(true), Some(List.empty)),
        None))
  }

  "cluster discovery actor" should "proceed with election when no /akka entry exists intially" in new Fixture {
    when(initReq).thenReturn(initSuccessResp)
    when(electionBidReq).thenReturn(electionBidSuccessResp)
    when(fetchSeedsReq).thenReturn(noSeedsResp)
    val discovery = init()
    discovery ! Start
    expectTransitionTo(Election)
    expectTransitionTo(Leader)
  }

  it should "proceed with election when /akka entry already exists" in new Fixture {
    when(initReq).thenReturn(initNodeExistsResp)
    when(electionBidReq).thenReturn(electionBidSuccessResp)
    when(fetchSeedsReq).thenReturn(noSeedsResp)
    val discovery = init()
    discovery ! Start
    expectTransitionTo(Election)
    expectTransitionTo(Leader)
  }

  it should "transition to Follower role after losing election" in new Fixture {
    when(initReq).thenReturn(initSuccessResp)
    when(electionBidReq).thenReturn(eleectionBidFailureResp)
    when(fetchSeedsReq).thenReturn(noSeedsResp)
    val discovery = init()
    discovery ! Start
    expectTransitionTo(Election)
    expectTransitionTo(Follower)
  }

  it should "tranistion to Leader role from Follower role when pevious leader leaves the cluster" in new Fixture {
    when(initReq).thenReturn(initSuccessResp)
    when(electionBidReq).thenReturn(eleectionBidFailureResp)
    when(fetchSeedsReq).thenReturn(noSeedsResp)
    val discovery = init()
    discovery ! Start
    expectTransitionTo(Election)
    expectTransitionTo(Follower)
    discovery ! LeaderChanged(Some(selfAddress))
    expectTransitionTo(Leader)
  }

  it should "retry election on transient errors" in new Fixture {
    when(initReq).thenReturn(initSuccessResp)
    when(electionBidReq).thenReturn(electionBidTransientFailureResp).thenReturn(electionBidSuccessResp)
    when(fetchSeedsReq).thenReturn(noSeedsResp)
    val discovery = init(settings.copy(etcdRetryDelay = 100.milliseconds))
    discovery ! Start
    expectTransitionTo(Election)
    expectTransitionTo(Leader)
  }
}
