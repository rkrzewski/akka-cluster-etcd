package pl.caltha.akka.cluster.monitor.frontend

import java.net.MalformedURLException
import akka.actor.Address
import akka.actor.AddressFromURIString
import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.cluster.UniqueAddress

import spray.json._
import spray.json.DefaultJsonProtocol._

import pl.caltha.akka.cluster.monitor.ShutdownCommand

object JsonProtocol extends DefaultJsonProtocol {

  implicit val addressFormat = new JsonFormat[Address] {
    def write(address: Address) = JsString(address.toString)
    def read(json: JsValue): Address = json match {
      case JsString(addr) ⇒ try AddressFromURIString(addr) catch {
        case t: MalformedURLException ⇒ deserializationError("malformed Address", t)
      }
      case x ⇒ deserializationError("expected Address as JsString but got " + x)
    }
  }

  implicit val uniqueAddressWriter = new JsonWriter[UniqueAddress] {
    def write(uniqueAddress: UniqueAddress) = JsObject(
      "address" → uniqueAddress.address.toJson,
      "uid" → uniqueAddress.uid.toJson)
  }

  implicit val memberStatusWriter = new JsonWriter[MemberStatus] {
    import MemberStatus._
    def write(memberStatus: MemberStatus) = memberStatus match {
      case Joining ⇒ JsString("Joining")
      case WeaklyUp ⇒ JsString("WeaklyUp")
      case Up ⇒ JsString("Up")
      case Leaving ⇒ JsString("Leaving")
      case Exiting ⇒ JsString("Exiting")
      case Down ⇒ JsString("Down")
      case Removed ⇒ JsString("Removed")
    }
  }

  implicit val memberWriter = new JsonWriter[Member] {
    def write(member: Member) = JsObject(
      "uniqueAddress" → member.uniqueAddress.toJson,
      "status" → member.status.toJson,
      "roles" → member.roles.toJson)
  }

  implicit val currentClusterStateWriter = new JsonWriter[CurrentClusterState] {
    def write(state: CurrentClusterState) = JsObject(
      "event" → JsString("InitialState"),
      "members" → JsArray(state.members.toSeq.map(_.toJson).toVector),
      "unreachable" → JsArray(state.unreachable.map(_.toJson).toVector),
      "leader" → state.leader.toJson,
      "roleLeaderMap" → state.roleLeaderMap.toJson)
  }

  implicit val memberEventWriter = new JsonWriter[MemberEvent] {
    def write(event: MemberEvent) = event match {
      case MemberJoined(member) ⇒ JsObject(
        "event" → JsString("MemberJoined"),
        "member" → member.toJson)
      case MemberWeaklyUp(member) ⇒ JsObject(
        "event" → JsString("MemberWeaklyUp"),
        "member" → member.toJson)
      case MemberUp(member) ⇒ JsObject(
        "event" → JsString("MemberUp"),
        "member" → member.toJson)
      case MemberLeft(member) ⇒ JsObject(
        "event" → JsString("MemberLeft"),
        "member" → member.toJson)
      case MemberExited(member) ⇒ JsObject(
        "event" → JsString("MemberExited"),
        "member" → member.toJson)
      case MemberRemoved(member, previousStatus) ⇒ JsObject(
        "event" → JsString("MemberRemoved"),
        "member" → member.toJson,
        "previousStatus" → previousStatus.toJson)
    }
  }

  implicit val reachabilityEventWriter = new JsonWriter[ReachabilityEvent] {
    def write(event: ReachabilityEvent) = event match {
      case ReachableMember(member) ⇒ JsObject(
        "event" → JsString("ReachableMember"),
        "member" → member.toJson)
      case UnreachableMember(member) ⇒ JsObject(
        "event" → JsString("UnreachableMember"),
        "member" → member.toJson)
    }
  }

  implicit val clusterDomainEventWriter = new JsonWriter[ClusterDomainEvent] {
    // @unchecked - we don't expect any internal cluster events here
    def write(event: ClusterDomainEvent) = (event: @unchecked) match {
      case e: ReachabilityEvent ⇒ e.toJson
      case e: MemberEvent ⇒ e.toJson
      case LeaderChanged(leader) ⇒ JsObject(
        "event" → JsString("LeaderChanged"),
        "leader" → leader.toJson)
      case RoleLeaderChanged(role, leader) ⇒ JsObject(
        "event" → JsString("RoleLeaderChanged"),
        "role" → JsString(role),
        "leader" → leader.toJson)
      case ClusterShuttingDown ⇒ JsObject(
        "event" → JsString("ClusterShuttingDown"))
    }
  }

  implicit val welcomeMessageWriter = new JsonWriter[WelcomeMessage] {
    def write(msg: WelcomeMessage) = JsObject(
      "event" → JsString("WelcomeMessage"),
      "address" → msg.address.toJson)
  }

  implicit val keepaliveMessageWriter = new JsonWriter[KeepaliveMessage.type] {
    def write(msg: KeepaliveMessage.type) = JsObject(
      "event" → JsString("Keepalive"))
  }

  implicit val shutdownCommandReader = jsonFormat2(ShutdownCommand)
}
