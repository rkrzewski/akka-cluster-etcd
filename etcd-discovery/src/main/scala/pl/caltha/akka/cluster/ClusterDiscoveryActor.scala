package pl.caltha.akka.cluster

import scala.collection.immutable.Set
import scala.concurrent.Future

import akka.actor.Address
import akka.actor.AddressFromURIString
import akka.actor.LoggingFSM
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.InitialStateAsSnapshot
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.pattern.pipe

import pl.caltha.akka.etcd.EtcdClient
import pl.caltha.akka.etcd.EtcdError
import pl.caltha.akka.etcd.EtcdException
import pl.caltha.akka.etcd.EtcdNode
import pl.caltha.akka.etcd.EtcdResponse
import akka.cluster.ClusterEvent.CurrentClusterState

class ClusterDiscoveryActor(
    etcdClient: EtcdClient,
    cluster: Cluster,
    settings: ClusterDiscoverySettings) extends LoggingFSM[ClusterDiscoveryActor.State, ClusterDiscoveryActor.Data] {

  import ClusterDiscoveryActor._

  private implicit val executor = context.system.dispatcher

  def etcd(operation: EtcdClient ⇒ Future[EtcdResponse]) =
    operation(etcdClient).recover {
      case ex: EtcdException ⇒ ex.error
    }.pipeTo(self)

  val seedList = context.actorOf(SeedListActor.props(etcdClient, settings))

  when(Initial) {
    case Event(Start, _) ⇒
      etcd(_.createDir(
        key = settings.etcdPath,
        ttl = None))
      stay()
    case Event(_: EtcdResponse, _) ⇒
      goto(Election)
    case Event(EtcdError(EtcdError.NodeExist, _, _, _), _) ⇒
      goto(Election)
    // I'd expect EtcdError.NodeExists, but that's what we get when /akka already exists
    case Event(EtcdError(EtcdError.NotFile, _, _, _), _) ⇒
      goto(Election)
  }

  onTransition {
    case (_, Election) ⇒
      etcd(_.compareAndSet(
        key = settings.leaderPath,
        value = cluster.selfAddress.toString,
        ttl = Some(settings.leaderEntryTTL.toSeconds.asInstanceOf[Int]),
        prevValue = None,
        prevIndex = None,
        prevExist = Some(false)))
  }

  when(Election) {
    case Event(_: EtcdResponse, _) ⇒
      goto(Leader)
    case Event(EtcdError(EtcdError.NodeExist, _, _, _), _) ⇒
      goto(Follower)
    case Event(err @ EtcdError, _) ⇒
      log.error(s"Election error: $err")
      stay()
  }

  onTransition {
    case (_, Leader) ⇒
      // bootstrap the cluster
      cluster.join(cluster.selfAddress)
      cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot, classOf[MemberEvent])
      context.actorOf(LeaderEntryActor.props(cluster.selfAddress.toString, etcdClient, settings))
  }

  when(Leader) {
    case Event(CurrentClusterState(members, _, _, _, _), _) ⇒
      seedList ! SeedListActor.InitialState(members.map(_.address.toString))
      stay().using(members.map(_.address))
    case Event(MemberUp(member), seeds) ⇒
      seedList ! SeedListActor.MemberAdded(member.address.toString)
      stay().using(seeds + member.address)
    case Event(MemberExited(member), seeds) ⇒
      seedList ! SeedListActor.MemberRemoved(member.address.toString)
      stay().using(seeds - member.address)
    case Event(MemberRemoved(member, _), seeds) ⇒
      if (seeds.contains(member.address))
        seedList ! SeedListActor.MemberRemoved(member.address.toString)
      stay().using(seeds - member.address)
  }

  def fetchSeeds() =
    etcd(_.get(
      key = settings.seedsPath,
      recursive = true,
      sorted = false))

  onTransition {
    case (_, Follower) ⇒
      setTimer("reelection", RetryElection, settings.seedsWaitTimeout, false)
      fetchSeeds()
  }

  when(Follower) {
    case Event(EtcdResponse("get", EtcdNode(_, _, _, _, _, Some(true), Some(nodes)), _), _) ⇒
      val seeds = nodes.flatMap(_.value).map(AddressFromURIString(_))
      cluster.joinSeedNodes(seeds)
      cancelTimer("reelection")
      stay()
    case Event(EtcdError(EtcdError.KeyNotFound, _, _, _), _) ⇒
      setTimer("retry", Retry, settings.etcdRetryDelay, false)
      stay()
    case Event(Retry, _) ⇒
      fetchSeeds()
      stay()
    case Event(RetryElection, _) ⇒
      goto(Election)
  }

  whenUnhandled {
    case Event(msg, _) ⇒
      log.warning(s"unhandled message $msg")
      stay()
  }

  startWith(Initial, Set.empty)
  initialize()
}

object ClusterDiscoveryActor {

  def props(etcd: EtcdClient, cluster: Cluster, settings: ClusterDiscoverySettings) =
    Props(classOf[ClusterDiscoveryActor], etcd, cluster, settings)

  /*
   * FSM States
   */
  sealed trait State
  case object Initial extends State
  case object Election extends State
  case object Leader extends State
  case object Follower extends State

  /**
   * FSM Data = known cluster nodes
   */
  type Data = Set[Address]

  /**
   * Message that triggers actor's initialization
   */
  case object Start
  case object Retry
  case object RetryElection

}
