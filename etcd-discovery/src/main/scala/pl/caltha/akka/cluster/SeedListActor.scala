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

/**
 * Helper actor that manages seed list in etcd, on behalf of `ClusterDiscoveryActor` in `Leader` state.
 */
class SeedListActor(
    etcdClient: EtcdClient,
    settings: ClusterDiscoverySettings) extends Actor with Stash with ActorLogging {

  import SeedListActor._

  private implicit val executionContext = context.system.dispatcher

  def awaitingCommand(seedEtcdKeys: Map[String, String]): Receive = {
    case op @ Add(member) =>
      etcdClient.create(settings.seedsPath, member.address.toString).pipeTo(self)
      context.become(awaitingEtcdReply(seedEtcdKeys, op))
    case op @ Remove(member) =>
      seedEtcdKeys.get(member.address.toString).foreach { key =>
        etcdClient.delete(key, false).pipeTo(self)
      }
      context.become(awaitingEtcdReply(seedEtcdKeys, op))
  }

  def awaitingEtcdReply(seedEtcdKeys: Map[String, String], op: Operation): Receive = {
    case Add(_) =>
      stash()
    case Remove(_) =>
      stash()
    case EtcdResponse("create", EtcdNode(key, _, _, _, Some(address), _, _), _) =>
      unstashAll()
      context.become(awaitingCommand(seedEtcdKeys + (address -> key)))
    case EtcdResponse("delete", EtcdNode(_, _, _, _, Some(address), _, _), _) =>
      unstashAll()
      context.become(awaitingCommand(seedEtcdKeys - address))
    case Status.Failure(t) =>
      log.warning(s"etcd operation $op failed ${t.getMessage}, will retry in ${settings.etcdRetryDelay}")
      context.system.scheduler.scheduleOnce(settings.etcdRetryDelay, self, op)
      unstashAll()
      context.become(awaitingCommand(seedEtcdKeys))
  }
  
  override def receive = awaitingCommand(Map.empty)

}

object SeedListActor {

  def props(etcdClient: EtcdClient, settings: ClusterDiscoverySettings) =
    Props(classOf[SeedListActor], etcdClient, settings)

  sealed trait Operation

  case class Add(member: Member) extends Operation

  case class Remove(member: Member) extends Operation

}