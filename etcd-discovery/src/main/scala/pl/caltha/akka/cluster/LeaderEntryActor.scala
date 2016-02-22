package pl.caltha.akka.cluster

import scala.concurrent.duration.FiniteDuration

import akka.actor.FSM
import akka.actor.FSM.Failure
import akka.actor.Props
import akka.actor.Status
import akka.pattern.pipe
import pl.caltha.akka.etcd.EtcdClient
import pl.caltha.akka.etcd.EtcdError
import pl.caltha.akka.etcd.EtcdException
import pl.caltha.akka.etcd.EtcdResponse

/**
  * Actor responsible for periodical refresh of the /leader entry in etcd
  *
  * @param address: the address of cluster leader node.
  * @param etcdClient: EtcdClient to use.
  * @param settings: cluster discovery settings
  */
class LeaderEntryActor(
  address:    String,
  etcdClient: EtcdClient,
  settings:   ClusterDiscoverySettings)
    extends FSM[LeaderEntryActor.State, LeaderEntryActor.Data] {

  import LeaderEntryActor._

  implicit val executionContext = context.dispatcher

  val refreshInterval = settings.leaderEntryTTL / 2

  /**
    * Refresh the entry at leader path, assuming that it exists and the current value is our node's address.
    *
    * This method is used during the normal refresh cycle.
    */
  def refreshLeaderEntry() =
    etcdClient.compareAndSet(
      key       = settings.leaderPath,
      value     = address,
      ttl       = Some(settings.leaderEntryTTL.toSeconds.asInstanceOf[Int]),
      prevValue = Some(address),
      prevExist = Some(true)).recover {
        case ex: EtcdException ⇒ ex.error
      }.pipeTo(self)

  /**
    * Create the leader entry, assuming it does not exist.
    *
    * This method is used when the leader entry has expired while the leader node was unable to reach etcd, or when
    * the leader entry was hijacked by another node. System operator will eventually shut down one of the contending
    * leaders, and if the current node prevails it will reclaim the leader entry after it expires.
    */
  def createLeaderEntry() =
    etcdClient.compareAndSet(
      key       = settings.leaderPath,
      value     = address,
      ttl       = Some(settings.leaderEntryTTL.toSeconds.asInstanceOf[Int]),
      prevExist = Some(false)).recover {
        case ex: EtcdException ⇒ ex.error
      }.pipeTo(self)

  when(Idle) {
    case Event(StateTimeout, Data(assumeEntryExists)) ⇒
      if (assumeEntryExists) refreshLeaderEntry()
      else createLeaderEntry()
      goto(AwaitingReply)
  }

  when(AwaitingReply) {
    case Event(EtcdResponse(_, _, _), Data(_)) ⇒
      goto(Idle).using(Data(true)).forMax(refreshInterval)
    // the entry either expired or was hijacked by another node
    case Event(EtcdError(EtcdError.TestFailed | EtcdError.KeyNotFound, _, _, _), Data(_)) ⇒
      goto(Idle).using(Data(false)).forMax(refreshInterval)
    // recoverable EtcdErrors
    case Event(err @ EtcdError(_, _, _, _), Data(assumeEntryExists)) ⇒
      log.error(s"etcd error $err")
      goto(Idle).using(Data(assumeEntryExists)).forMax(settings.etcdRetryDelay)
    // network errors
    case Event(Status.Failure(t), Data(assumeEntryExists)) ⇒
      log.error(t, "etcd error")
      goto(Idle).using(Data(assumeEntryExists)).forMax(settings.etcdRetryDelay)
  }

  startWith(Idle, Data(true), Some(refreshInterval))
  initialize()
}

object LeaderEntryActor {

  def props(address: String, etcdClient: EtcdClient, settings: ClusterDiscoverySettings) =
    Props(classOf[LeaderEntryActor], address, etcdClient, settings)

  trait State
  case object Idle extends State
  case object AwaitingReply extends State

  case class Data(assumeEntryExists: Boolean)
}
