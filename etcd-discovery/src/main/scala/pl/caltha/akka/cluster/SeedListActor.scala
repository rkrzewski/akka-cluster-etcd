package pl.caltha.akka.cluster

import scala.collection.immutable.Map

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Stash
import akka.actor.Status
import akka.cluster.Member
import akka.pattern.pipe

import pl.caltha.akka.etcd.EtcdClient
import pl.caltha.akka.etcd.EtcdNode
import pl.caltha.akka.etcd.EtcdResponse
import pl.caltha.akka.etcd.EtcdException
import pl.caltha.akka.etcd.EtcdError

/**
 * Helper actor that manages seed list in etcd, on behalf of `ClusterDiscoveryActor` in `Leader` state.
 */
class SeedListActor(
    etcdClient: EtcdClient,
    settings: ClusterDiscoverySettings) extends Actor with Stash with ActorLogging {

  import SeedListActor._

  private implicit val executionContext = context.system.dispatcher

  def idle: Receive = {
    case ManageSeeds =>
      fetchStaleSeeds()
      context.become(fetchingStaleSeeds)
  }

  def fetchStaleSeeds() =
    etcdClient.get(settings.seedsPath, true, false).pipeTo(self)

  def fetchingStaleSeeds: Receive = {
    case Add | Remove =>
      stash()
    case EtcdResponse("get", EtcdNode(_, _, _, _, _, _, seedsOpt), _) =>
      seedsOpt match {
        case None =>
          context.become(awaitingCommand())
        case Some(seeds) =>
          context.become(deletingStateSeeds(seeds.map(_.key)))
      }
    case Status.Failure(EtcdException(EtcdError(EtcdError.KeyNotFound, _, settings.seedsPath, _))) =>
      context.become(awaitingCommand())
    case Status.Failure(EtcdException(err)) =>
      log.warning(s"etcd operation failed while fetching stale seeds: ${err}, will retry in ${settings.etcdRetryDelay}")
      context.system.scheduler.scheduleOnce(settings.etcdRetryDelay) {
        fetchStaleSeeds
      }
    case Status.Failure(t) =>
      log.warning(s"etcd operation failed while fetching stale seeds: ${t.toString}, will retry in ${settings.etcdRetryDelay}")
      context.system.scheduler.scheduleOnce(settings.etcdRetryDelay) {
        fetchStaleSeeds
      }
  }

  def deleteStaleSeed(key: String) =
    etcdClient.delete(key, false).pipeTo(self)

  def deletingStateSeeds(seedKeys: Seq[String]): Receive = seedKeys match {
    case Seq() =>
      awaitingCommand(Map.empty)
    case seedKey +: seedKeys =>
      deleteStaleSeed(seedKey)
      awaitingDelteReply(seedKey, seedKeys)
  }

  def awaitingDelteReply(seedKey: String, seedKeys: Seq[String]): Receive = {
    case EtcdResponse("delete", _, _) =>
      context.become(deletingStateSeeds(seedKeys))
    case Status.Failure(EtcdException(err)) =>
      log.warning(s"etcd operation failed while deleting stale seed ${seedKey}: ${err}, will retry in ${settings.etcdRetryDelay}")
      context.system.scheduler.scheduleOnce(settings.etcdRetryDelay) {
        deleteStaleSeed(seedKey)
      }
    case Status.Failure(t) =>
      log.warning(s"etcd operation failed while deleting stale seed ${seedKey}: ${t.toString}, will retry in ${settings.etcdRetryDelay}")
      context.system.scheduler.scheduleOnce(settings.etcdRetryDelay) {
        deleteStaleSeed(seedKey)
      }
  }

  def awaitingCommand(seedEtcdKeys: Map[String, String] = Map.empty): Receive = {
    case op @ Add(member) =>
      etcdClient.create(settings.seedsPath, member.address.toString).pipeTo(self)
      context.become(awaitingCommandReply(seedEtcdKeys, op))
    case op @ Remove(member) =>
      seedEtcdKeys.get(member.address.toString).foreach { key =>
        etcdClient.delete(key, false).pipeTo(self)
      }
      context.become(awaitingCommandReply(seedEtcdKeys, op))
  }

  def awaitingCommandReply(seedEtcdKeys: Map[String, String], op: Operation): Receive = {
    case Add | Remove =>
      stash()
    case EtcdResponse("create", EtcdNode(key, _, _, _, Some(address), _, _), _) =>
      unstashAll()
      context.become(awaitingCommand(seedEtcdKeys + (address -> key)))
    case EtcdResponse("delete", EtcdNode(_, _, _, _, Some(address), _, _), _) =>
      unstashAll()
      context.become(awaitingCommand(seedEtcdKeys - address))
    case Status.Failure(EtcdException(err)) =>
      log.warning(s"etcd operation $op failed: ${err}, will retry in ${settings.etcdRetryDelay}")
      context.system.scheduler.scheduleOnce(settings.etcdRetryDelay, self, op)
      unstashAll()
      context.become(awaitingCommand(seedEtcdKeys))
    case Status.Failure(t) =>
      log.warning(s"etcd operation $op failed: ${t.toString}, will retry in ${settings.etcdRetryDelay}")
      context.system.scheduler.scheduleOnce(settings.etcdRetryDelay, self, op)
      unstashAll()
      context.become(awaitingCommand(seedEtcdKeys))
  }

  override def receive = idle
}

object SeedListActor {

  def props(etcdClient: EtcdClient, settings: ClusterDiscoverySettings) =
    Props(classOf[SeedListActor], etcdClient, settings)

  case object ManageSeeds

  sealed trait Operation

  case class Add(member: Member) extends Operation

  case class Remove(member: Member) extends Operation

}