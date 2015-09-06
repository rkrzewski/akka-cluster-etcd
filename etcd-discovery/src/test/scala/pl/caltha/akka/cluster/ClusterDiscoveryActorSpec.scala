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
import akka.testkit.TestFSMRef
import akka.testkit.TestKit
import akka.testkit.TestProbe
import pl.caltha.akka.etcd.EtcdClient
import pl.caltha.akka.etcd.EtcdNode
import pl.caltha.akka.etcd.EtcdResponse
import pl.caltha.akka.etcd.EtcdException
import pl.caltha.akka.etcd.EtcdError
import pl.caltha.akka.etcd.EtcdCommandException

class ClusterDiscoveryActorSpec(_system: ActorSystem)
    extends TestKit(_system) with FlatSpecLike with Matchers with MockitoSugar {

  import Mockito.{ when, verify }
  import ClusterDiscoveryActor._

  def this() =
    this(ActorSystem("ClusterDiscoveryActorSpec"))

  val settings = ClusterDiscoverySettings.load(system.settings.config)

  val transitionTimeout = 3.seconds

  val selfAddress = Address("akka", "testsystem")

  trait Fixture {

    val etcd = mock[EtcdClient]

    val cluster = mock[Cluster]

    when(cluster.selfAddress).thenReturn(selfAddress)

    val stateProbe = TestProbe()

    def init(testSettings: ClusterDiscoverySettings = settings) = {
      val discovery = TestFSMRef(new ClusterDiscoveryActor(etcd, cluster, testSettings))
      discovery ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsgType[CurrentState[State]](transitionTimeout) should matchPattern {
        case CurrentState(_, Initial) =>
      }
      discovery
    }

    def nextTransition =
      stateProbe.expectMsgType[Transition[State]](transitionTimeout)

    implicit class TransitionExpectation(transition: Transition[State]) {
      def shouldBeToState(expState: State) =
        transition should matchPattern {
          case Transition(_, _, state) if state == expState =>
        }
    }

    def initReq =
      when(etcd.createDir(settings.etcdPath, None))

    val initSuccessResp = Future.successful(
      EtcdResponse("created",
        EtcdNode(settings.etcdPath, 0, 0, None, None, Some(true), Some(List.empty)),
        None))

    val initNodeExistsResp = Future.failed(
      EtcdException(
        EtcdError(EtcdError.NodeExist, "", "", 0)))

    def electionBidReq =
      when(etcd.compareAndSet(
        settings.leaderPath,
        selfAddress.toString,
        Some(settings.leaderEntryTTL.toSeconds.asInstanceOf[Int]),
        None,
        None,
        Some(false)))

    val electionBidSuccessResp = Future.successful(
      EtcdResponse("created",
        EtcdNode(settings.etcdPath, 0, 0, None, None, Some(true), Some(List.empty)),
        None))
  }

  "cluster discovery actor" should "proceed with election when no /akka entry exists intially" in new Fixture {
    initReq.thenReturn(initSuccessResp)
    electionBidReq.thenReturn(electionBidSuccessResp)
    val discovery = init()
    discovery ! Start
    nextTransition shouldBeToState Election
    nextTransition shouldBeToState Leader
  }
  
  "cluster discovery actor" should "proceed with election when /akka entry already exists" in new Fixture {
    initReq.thenReturn(initNodeExistsResp)
    electionBidReq.thenReturn(electionBidSuccessResp)
    val discovery = init()
    discovery ! Start
    nextTransition shouldBeToState Election
    nextTransition shouldBeToState Leader
  }
}