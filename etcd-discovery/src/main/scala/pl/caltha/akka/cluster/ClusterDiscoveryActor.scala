package pl.caltha.akka.cluster

import scala.collection.immutable.Set
import scala.concurrent.Future

import akka.actor.Address
import akka.actor.AddressFromURIString
import akka.actor.LoggingFSM
import akka.actor.Props
import akka.actor.Status
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
import akka.cluster.ClusterEvent._
import akka.cluster.Member

class ClusterDiscoveryActor(
    etcdClient: EtcdClient,
    cluster:    Cluster,
    settings:   ClusterDiscoverySettings) extends LoggingFSM[ClusterDiscoveryActor.State, ClusterDiscoveryActor.Data] {

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

  def electionBid() =
    etcd(_.compareAndSet(
      key       = settings.leaderPath,
      value     = cluster.selfAddress.toString,
      ttl       = Some(settings.leaderEntryTTL.toSeconds.asInstanceOf[Int]),
      prevValue = None,
      prevIndex = None,
      prevExist = Some(false)))

  onTransition {
    case (_, Election) ⇒
      log.info("starting election")
      electionBid()
  }

  when(Election) {
    case Event(_: EtcdResponse, _) ⇒
      goto(Leader)
    case Event(EtcdError(EtcdError.NodeExist, _, _, _), _) ⇒
      goto(Follower)
    case Event(err @ EtcdError, _) ⇒
      log.error(s"Election error: $err")
      setTimer("retry", Retry, settings.etcdRetryDelay, false)
      stay()
    case Event(Status.Failure(t), _) ⇒
      log.error(t, "Election error")
      setTimer("retry", Retry, settings.etcdRetryDelay, false)
      stay()
    case Event(Retry, _) ⇒
      log.warning("retrying")
      electionBid()
      stay()
  }

  onTransition {
    case (_, Leader) ⇒
      // bootstrap the cluster
      log.info("assuming Leader role")
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
    case Event(_: ClusterDomainEvent, _) ⇒
      stay()
  }

  def fetchSeeds() =
    etcd(_.get(
      key       = settings.seedsPath,
      recursive = true,
      sorted    = false))

  onTransition {
    case (_, Follower) ⇒
      log.info("assuming Follower role")
      cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot, classOf[MemberEvent])
      setTimer("seedsFetch", SeedsFetchTimeout, settings.seedsFetchTimeout, false)
      fetchSeeds()
  }

  when(Follower) {
    case Event(EtcdResponse("get", EtcdNode(_, _, _, _, _, Some(true), Some(nodes)), _), _) ⇒
      val seeds = nodes.flatMap(_.value).map(AddressFromURIString(_))
      log.info("attempting to join {}", seeds)
      cluster.joinSeedNodes(seeds)
      cancelTimer("seedsFetch")
      setTimer("seedsJoin", JoinTimeout, settings.seedsJoinTimeout, false)
      stay()
    case Event(EtcdError(EtcdError.KeyNotFound, _, _, _), _) ⇒
      setTimer("retry", Retry, settings.etcdRetryDelay, false)
      stay()
    case Event(Retry, _) ⇒
      fetchSeeds()
      stay()
    case Event(SeedsFetchTimeout, _) ⇒
      log.info(s"failed to fetch seed node information in ${settings.seedsFetchTimeout.toMillis} ms")
      goto(Election)
    case Event(JoinTimeout, _) ⇒
      log.info(s"seed nodes failed to respond in ${settings.seedsJoinTimeout.toMillis} ms")
      goto(Election)
    case Event(LeaderChanged(Some(address)), _) if address == cluster.selfAddress ⇒
      goto(Leader)
    case Event(LeaderChanged(optAddress), _) ⇒
      log.info(s"seen leader change to $optAddress")
      stay()
    case Event(MemberUp(member), _) if member.address == cluster.selfAddress ⇒
      log.info("joined the cluster")
      cancelTimer("seedsFetch")
      cancelTimer("seedsJoin")
      stay()
    case Event(_: ClusterDomainEvent | CurrentClusterState, _) ⇒
      stay()
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
  private case object SeedsFetchTimeout
  private case object JoinTimeout

}
