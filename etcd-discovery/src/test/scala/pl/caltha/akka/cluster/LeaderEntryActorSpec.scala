package pl.caltha.akka.cluster

import java.time.ZonedDateTime

import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt

import org.mockito.Mockito

import akka.actor.FSM.SubscribeTransitionCallBack
import akka.stream.StreamTcpException

import me.maciejb.etcd.client.EtcdError
import me.maciejb.etcd.client.EtcdException
import me.maciejb.etcd.client.EtcdNode
import me.maciejb.etcd.client.EtcdResponse

class LeaderEntryActorSpec extends EtcdFSMSpecBase[LeaderEntryActor.State, LeaderEntryActor.Data] {

  import LeaderEntryActor._
  import Mockito.when

  override def settings = ClusterDiscoverySettings.load(system.settings.config).copy(
    etcdRetryDelay = 500.milliseconds,
    leaderEntryTTL = 1.second)

  override def transitionTimeout = 1.second

  val address = "leaderAddress"

  trait Fixture extends FixtureBase {

    def init(testSettings: ClusterDiscoverySettings = settings) = {
      val leaderEntryActor = system.actorOf(LeaderEntryActor.props(address, etcd, testSettings))
      leaderEntryActor ! SubscribeTransitionCallBack(stateProbe.ref)
      expectInitialState(Idle)
      leaderEntryActor
    }

    def refreshReq =
      etcd.compareAndSet(
        key       = settings.leaderPath,
        value     = address,
        ttl       = Some(settings.leaderEntryTTL.toSeconds.asInstanceOf[Int]),
        prevValue = Some(address),
        prevExist = Some(true))

    def recreateReq =
      etcd.compareAndSet(
        key       = settings.leaderPath,
        value     = address,
        ttl       = Some(settings.leaderEntryTTL.toSeconds.asInstanceOf[Int]),
        prevExist = Some(false))

    def refreshSuccessResp =
      EtcdResponse(
        "set",
        EtcdNode(
          settings.leaderPath,
          100,
          100,
          Option(ZonedDateTime.now()),
          Some(address),
          None,
          None),
        None)

    def refreshIntFailureResp =
      EtcdError(EtcdError.LeaderElect, "Retry later", "", 100)

    def refreshNotFoundResp =
      EtcdError(EtcdError.KeyNotFound, "Key not found", settings.leaderPath, 100)

    def refreshCompareFailedResp =
      EtcdError(EtcdError.TestFailed, "Compare failed", s"${address} != other", 100)
  }

  "leader entry actor" should "refresh the entry at TTL / 2 periods" in new Fixture {
    val refreshPromise1 = Promise[EtcdResponse]
    val refreshPromise2 = Promise[EtcdResponse]
    when(refreshReq).thenReturn(refreshPromise1.future).thenReturn(refreshPromise2.future)

    val leaderEntryActor = init()
    expectTransitionTo(AwaitingReply)
    refreshPromise1.success(refreshSuccessResp)
    expectTransitionTo(Idle)

    expectTransitionTo(AwaitingReply)
    refreshPromise2.success(refreshSuccessResp)
    expectTransitionTo(Idle)
  }

  it should "retry opreations failed because of etcd internal errors" in new Fixture {
    val refreshPromise1 = Promise[EtcdResponse]
    val refreshPromise2 = Promise[EtcdResponse]
    when(refreshReq).thenReturn(refreshPromise1.future).thenReturn(refreshPromise2.future)

    val leaderEntryActor = init()
    expectTransitionTo(AwaitingReply)
    refreshPromise1.failure(EtcdException(refreshIntFailureResp))
    expectTransitionTo(Idle)

    expectTransitionTo(AwaitingReply)
    refreshPromise2.success(refreshSuccessResp)
    expectTransitionTo(Idle)
  }

  it should "retry opreations failed because of transport level errors" in new Fixture {
    val refreshPromise1 = Promise[EtcdResponse]
    val refreshPromise2 = Promise[EtcdResponse]
    when(refreshReq).thenReturn(refreshPromise1.future).thenReturn(refreshPromise2.future)

    val leaderEntryActor = init()
    expectTransitionTo(AwaitingReply)
    refreshPromise1.failure(new StreamTcpException("Connection failed"))
    expectTransitionTo(Idle)

    expectTransitionTo(AwaitingReply)
    refreshPromise2.success(refreshSuccessResp)
    expectTransitionTo(Idle)
  }

  it should "attempt to recreate leader entry when it expires" in new Fixture {
    val refreshPromise1 = Promise[EtcdResponse]
    val refreshPromise2 = Promise[EtcdResponse]
    val recreatePromise = Promise[EtcdResponse]
    when(refreshReq).thenReturn(refreshPromise1.future).thenReturn(refreshPromise2.future)
    when(recreateReq).thenReturn(recreatePromise.future)

    val leaderEntryActor = init()
    expectTransitionTo(AwaitingReply)
    refreshPromise1.failure(EtcdException(refreshNotFoundResp))
    expectTransitionTo(Idle)

    expectTransitionTo(AwaitingReply)
    recreatePromise.success(refreshSuccessResp)
    expectTransitionTo(Idle)

    expectTransitionTo(AwaitingReply)
    refreshPromise2.success(refreshSuccessResp)
    expectTransitionTo(Idle)
  }

  it should "attempt to recreate leader entry when it is hijacked by another node" in new Fixture {
    val refreshPromise1 = Promise[EtcdResponse]
    val refreshPromise2 = Promise[EtcdResponse]
    val recreatePromise = Promise[EtcdResponse]
    when(refreshReq).thenReturn(refreshPromise1.future).thenReturn(refreshPromise2.future)
    when(recreateReq).thenReturn(recreatePromise.future)

    val leaderEntryActor = init()
    expectTransitionTo(AwaitingReply)
    refreshPromise1.failure(EtcdException(refreshCompareFailedResp))
    expectTransitionTo(Idle)

    expectTransitionTo(AwaitingReply)
    recreatePromise.success(refreshSuccessResp)
    expectTransitionTo(Idle)

    expectTransitionTo(AwaitingReply)
    refreshPromise2.success(refreshSuccessResp)
    expectTransitionTo(Idle)
  }
}
