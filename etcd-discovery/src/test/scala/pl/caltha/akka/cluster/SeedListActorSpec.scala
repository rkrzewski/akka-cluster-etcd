package pl.caltha.akka.cluster

import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt

import org.mockito.Mockito

import akka.actor.FSM.SubscribeTransitionCallBack
import akka.actor.actorRef2Scala

import pl.caltha.akka.etcd.EtcdError
import pl.caltha.akka.etcd.EtcdException
import pl.caltha.akka.etcd.EtcdNode
import pl.caltha.akka.etcd.EtcdResponse

class SeedListActorSpec extends EtcdFSMSpecBase[SeedListActor.State, SeedListActor.Data] {

  import SeedListActor._
  import Mockito.{ when }

  trait Fixture extends FixtureBase {

    def init(testSettings: ClusterDiscoverySettings = settings) = {
      val seedList = system.actorOf(SeedListActor.props(etcd, testSettings))
      seedList ! SubscribeTransitionCallBack(stateProbe.ref)
      expectInitialState(AwaitingInitialState)
      seedList
    }

    def fetchSeedsReq =
      etcd.get(settings.seedsPath, true)

    val noSeedsResp =
      EtcdResponse("get",
        EtcdNode(settings.seedsPath, 100, 132, None, None, Some(true), Some(List.empty)),
        None)

    val staleSeedsResp =
      EtcdResponse("get",
        EtcdNode(settings.seedsPath, 100, 132, None, None, Some(true), Some(List(
          EtcdNode(s"${settings.seedsPath}/131", 131, 131, None, Some(addr1), None, None),
          EtcdNode(s"${settings.seedsPath}/132", 132, 132, None, Some(addr2), None, None)))),
        None)

    def deleteReq(key: Int) =
      etcd.delete(s"${settings.seedsPath}/$key", false)

    def deleteReq1 =
      deleteReq(131)

    def deleteReq2 =
      deleteReq(132)

    def deleteResp(deletedKey: Int, createdKey: Int, address: String) =
      EtcdResponse("delete",
        EtcdNode(s"${settings.seedsPath}/$createdKey", createdKey, deletedKey, None, None, None, None),
        Some(EtcdNode(s"${settings.seedsPath}/$createdKey", createdKey, createdKey, None, Some(address), None, None)))

    val addr1 = "akka.tcp://system@host1:50000"

    val addr2 = "akka.tcp://system@host2:50000"

    def deleteResp1 =
      deleteResp(135, 131, addr1)

    def deleteResp2 =
      deleteResp(135, 132, addr2)

    def createReq(address: String) =
      etcd.create(settings.seedsPath, address)

    def createReq1 =
      createReq(addr1)

    def createReq2 =
      createReq(addr2)

    def createResp(key: Int, address: String) =
      EtcdResponse("create",
        EtcdNode(s"${settings.seedsPath}/$key", key, key, None, Some(address), None, None),
        None)

    def createResp1 =
      createResp(131, addr1)

    def createResp2 =
      createResp(132, addr2)

    val failure =
      EtcdException(EtcdError(EtcdError.RaftInternal, "", "", 100))
  }

  "seed list manager actor" should "proceed to AwaitCommand when seed lists are empty" in new Fixture {
    val seedsPromise = Promise[EtcdResponse]
    when(fetchSeedsReq).thenReturn(seedsPromise.future)
    val seedList = init()

    seedList ! InitialState(Set.empty)
    expectTransitionTo(AwaitingRegisterdSeeds)

    seedsPromise.success(noSeedsResp)
    expectTransitionTo(AwaitingCommand)
  }

  it should "delete stale seeds" in new Fixture {
    val seedsPromise = Promise[EtcdResponse]
    val deletePromise1 = Promise[EtcdResponse]
    val deletePromise2 = Promise[EtcdResponse]
    when(fetchSeedsReq).thenReturn(seedsPromise.future)
    when(deleteReq1).thenReturn(deletePromise1.future)
    when(deleteReq2).thenReturn(deletePromise2.future)
    val seedList = init()

    seedList ! InitialState(Set.empty)
    expectTransitionTo(AwaitingRegisterdSeeds)

    seedsPromise.success(staleSeedsResp)
    expectTransitionTo(AwaitingCommand)
    expectTransitionTo(AwaitingEtcdReply)

    deletePromise1.success(deleteResp1)
    deletePromise2.success(deleteResp2)
    expectTransitionTo(AwaitingCommand)
  }

  it should "register initial seeds" in new Fixture {
    val seedsPromise = Promise[EtcdResponse]
    val createPromise1 = Promise[EtcdResponse]
    val createPromise2 = Promise[EtcdResponse]
    when(fetchSeedsReq).thenReturn(seedsPromise.future)
    when(createReq1).thenReturn(createPromise1.future)
    when(createReq2).thenReturn(createPromise2.future)
    val seedList = init()

    seedList ! InitialState(Set(addr1, addr2))
    expectTransitionTo(AwaitingRegisterdSeeds)

    seedsPromise.success(noSeedsResp)
    expectTransitionTo(AwaitingCommand)
    expectTransitionTo(AwaitingEtcdReply)

    createPromise1.success(createResp1)
    createPromise2.success(createResp2)
    expectTransitionTo(AwaitingCommand)
  }

  it should "handle MemberAdded / MemberRemoved commands" in new Fixture {
    val seedsPromise = Promise[EtcdResponse]
    val createPromise1 = Promise[EtcdResponse]
    val deletePromise1 = Promise[EtcdResponse]
    when(fetchSeedsReq).thenReturn(seedsPromise.future)
    when(createReq1).thenReturn(createPromise1.future)
    when(deleteReq1).thenReturn(deletePromise1.future)

    val seedList = init()
    seedList ! InitialState(Set.empty)
    expectTransitionTo(AwaitingRegisterdSeeds)

    seedsPromise.success(noSeedsResp)
    expectTransitionTo(AwaitingCommand)

    seedList ! MemberAdded(addr1)
    expectTransitionTo(AwaitingEtcdReply)

    createPromise1.success(createResp1)
    expectTransitionTo(AwaitingCommand)

    seedList ! MemberRemoved(addr1)
    expectTransitionTo(AwaitingEtcdReply)
    deletePromise1.success(deleteResp1)
    expectTransitionTo(AwaitingCommand)
  }

  it should "retry fetching registered seeds in case of errors" in new Fixture {
    val seedsErrorPromise = Promise[EtcdResponse]
    val seedsSuccessPromise = Promise[EtcdResponse]
    when(fetchSeedsReq).thenReturn(seedsErrorPromise.future).thenReturn(seedsSuccessPromise.future)

    val seedList = init(settings.copy(etcdRetryDelay = 500.milliseconds))
    seedList ! InitialState(Set.empty)
    expectTransitionTo(AwaitingRegisterdSeeds)

    seedsErrorPromise.failure(failure)
    expectTransitionTo(AwaitingInitialState)
    expectTransitionTo(AwaitingRegisterdSeeds)

    seedsSuccessPromise.success(noSeedsResp)
    expectTransitionTo(AwaitingCommand)
  }

  it should "retry Add / Remove operations in case of errors" in new Fixture {
    val seedsPromise = Promise[EtcdResponse]
    val createPromiseError = Promise[EtcdResponse]
    val createPromise1 = Promise[EtcdResponse]
    val deletePromiseError = Promise[EtcdResponse]
    val deletePromise1 = Promise[EtcdResponse]
    when(fetchSeedsReq).thenReturn(seedsPromise.future)
    when(createReq1).thenReturn(createPromiseError.future).thenReturn(createPromise1.future)
    when(deleteReq1).thenReturn(deletePromiseError.future).thenReturn(deletePromise1.future)

    val seedList = init(settings.copy(etcdRetryDelay = 500.milliseconds))
    seedList ! InitialState(Set.empty)
    expectTransitionTo(AwaitingRegisterdSeeds)

    seedsPromise.success(noSeedsResp)
    expectTransitionTo(AwaitingCommand)

    seedList ! MemberAdded(addr1)
    expectTransitionTo(AwaitingEtcdReply)

    createPromiseError.failure(failure)
    expectTransitionTo(AwaitingCommand)
    expectTransitionTo(AwaitingEtcdReply)

    createPromise1.success(createResp1)
    expectTransitionTo(AwaitingCommand)

    seedList ! MemberRemoved(addr1)
    expectTransitionTo(AwaitingEtcdReply)

    deletePromiseError.failure(failure)
    expectTransitionTo(AwaitingCommand)
    expectTransitionTo(AwaitingEtcdReply)

    deletePromise1.success(deleteResp1)
    expectTransitionTo(AwaitingCommand)
  }
}