package pl.caltha.akka.cluster

import scala.concurrent.Future

import akka.actor.FSM
import akka.actor.Props
import akka.actor.Stash
import akka.actor.Status
import akka.pattern.pipe

import pl.caltha.akka.etcd.EtcdClient
import pl.caltha.akka.etcd.EtcdError
import pl.caltha.akka.etcd.EtcdException
import pl.caltha.akka.etcd.EtcdNode
import pl.caltha.akka.etcd.EtcdResponse

class SeedListActor(
  etcdClient: EtcdClient,
  settings:   ClusterDiscoverySettings)
    extends FSM[SeedListActor.State, SeedListActor.Data] with Stash {

  import SeedListActor._

  private implicit val executionContext = context.dispatcher

  private def etcd(operation: EtcdClient ⇒ Future[EtcdResponse]) =
    operation(etcdClient).recover {
      case ex: EtcdException ⇒ ex.error
    }.pipeTo(self)

  private def retryMsg(msg: Any): Unit =
    context.system.scheduler.scheduleOnce(settings.etcdRetryDelay) {
      self ! msg
    }

  when(AwaitingInitialState) {
    case Event(InitialState(members), _) ⇒
      etcd(_.get(settings.seedsPath, true, false))
      goto(AwaitingRegisterdSeeds).using(AwaitingRegisterdSeedsData(members))
    case Event(MemberAdded(_) | MemberRemoved(_), _) ⇒
      stash()
      stay()
  }

  when(AwaitingRegisterdSeeds) {
    case Event(EtcdResponse("get", EtcdNode(_, _, _, _, _, _, seedsOpt), _),
      AwaitingRegisterdSeedsData(currentSeeds)) ⇒
      seedsOpt match {
        case None ⇒
          unstashAll()
          goto(AwaitingCommand).using(AwaitingCommandData(Map.empty))
        case Some(seeds) ⇒
          val registeredSeeds = seeds.flatMap(_.value).toSet
          (currentSeeds -- registeredSeeds).foreach { member ⇒
            self ! MemberAdded(member)
          }
          (registeredSeeds -- currentSeeds).foreach { member ⇒
            self ! MemberRemoved(member)
          }
          val addressMapping = for {
            node ← seeds
            value ← node.value
          } yield value → node.key
          unstashAll()
          goto(AwaitingCommand).using(AwaitingCommandData(addressMapping.toMap))
      }
    case Event(EtcdError(EtcdError.KeyNotFound, _, settings.seedsPath, _),
      AwaitingRegisterdSeedsData(current)) ⇒
      current.foreach { member ⇒
        self ! MemberAdded(member)
      }
      unstashAll()
      goto(AwaitingCommand).using(AwaitingCommandData(Map.empty))
    case Event(e: EtcdError, AwaitingRegisterdSeedsData(current)) ⇒
      log.warning(s"etcd error while fetching registered seeds: $e")
      retryMsg(InitialState(current))
      goto(AwaitingInitialState).using(AwaitingInitialStateData)
    case Event(Status.Failure(t), AwaitingRegisterdSeedsData(current)) ⇒
      log.warning(s"etcd error while fetching registered seeds", t)
      retryMsg(InitialState(current))
      goto(AwaitingInitialState).using(AwaitingInitialStateData)
    case Event(MemberAdded(_) | MemberRemoved(_), _) ⇒
      stash()
      stay()
  }

  when(AwaitingCommand) {
    case Event(command @ MemberAdded(address), AwaitingCommandData(addressMapping)) ⇒
      etcd(_.create(settings.seedsPath, address))
      goto(AwaitingEtcdReply).using(AwaitingEtcdReplyData(command, addressMapping))
    case Event(command @ MemberRemoved(address), AwaitingCommandData(addressMapping)) ⇒
      addressMapping.get(address) match {
        case Some(key) ⇒
          etcd(_.delete(key, false))
          goto(AwaitingEtcdReply).using(AwaitingEtcdReplyData(command, addressMapping))
        case None ⇒
          stay()
      }
  }

  when(AwaitingEtcdReply) {
    case Event(EtcdResponse("create", EtcdNode(key, _, _, _, Some(address), _, _), _),
      AwaitingEtcdReplyData(_, addressMapping)) ⇒
      unstashAll()
      goto(AwaitingCommand).using(AwaitingCommandData(addressMapping + (address → key)))
    case Event(EtcdResponse("delete", _, Some(EtcdNode(_, _, _, _, Some(address), _, _))),
      AwaitingEtcdReplyData(_, addressMapping)) ⇒
      unstashAll()
      goto(AwaitingCommand).using(AwaitingCommandData(addressMapping - address))
    case Event(e: EtcdError, AwaitingEtcdReplyData(command, addressMapping)) ⇒
      log.warning(s"etcd error while handing ${command}: $e")
      retryMsg(command)
      unstashAll()
      goto(AwaitingCommand).using(AwaitingCommandData(addressMapping))
    case Event(Status.Failure(t), AwaitingEtcdReplyData(command, addressMapping)) ⇒
      log.warning(s"etcd error while fetching handing ${command}", t)
      retryMsg(command)
      unstashAll()
      goto(AwaitingCommand).using(AwaitingCommandData(addressMapping))
    case Event(MemberAdded(_) | MemberRemoved(_), _) ⇒
      stash()
      stay()
  }

  startWith(AwaitingInitialState, AwaitingInitialStateData)
  initialize()
}

object SeedListActor {

  def props(etcdClient: EtcdClient, settings: ClusterDiscoverySettings) =
    Props(classOf[SeedListActor], etcdClient, settings)

  sealed trait State
  case object AwaitingInitialState extends State
  case object AwaitingRegisterdSeeds extends State
  case object AwaitingCommand extends State
  case object AwaitingEtcdReply extends State

  sealed trait Data
  case object AwaitingInitialStateData extends Data
  case class AwaitingRegisterdSeedsData(currentSeeds: Set[String]) extends Data
  case class AwaitingCommandData(addressMapping: Map[String, String]) extends Data
  case class AwaitingEtcdReplyData(command: Command, addressMapping: Map[String, String]) extends Data

  case class InitialState(members: Set[String])
  sealed trait Command
  case class MemberAdded(member: String) extends Command
  case class MemberRemoved(member: String) extends Command
}