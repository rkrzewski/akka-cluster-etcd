package pl.caltha.akka.cluster.monitor

import scala.annotation.tailrec
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.stream.actor._
import akka.actor.Props

class ClusterEventPublisher(maxBufferSize: Int) extends ActorPublisher[ClusterDomainEvent] {
  import ActorPublisherMessage._

  Cluster(context.system).subscribe(self, InitialStateAsSnapshot, classOf[MemberEvent],
    classOf[ReachabilityEvent], classOf[LeaderChanged], classOf[RoleLeaderChanged])

  var buf = Vector.empty[ClusterDomainEvent]

  def receive = {
    case e: ClusterDomainEvent if buf.size == maxBufferSize ⇒
      onErrorThenStop(new IndexOutOfBoundsException("buffer overflow"))
    case e: ClusterDomainEvent ⇒
      if (buf.isEmpty && totalDemand > 0)
        onNext(e)
      else {
        buf :+= e
        deliver()
      }
    case Request(_) ⇒
      deliver()
    case Cancel ⇒
      context.stop(self)
  }

  @tailrec final def deliver(): Unit =
    if (totalDemand > 0) {
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        use foreach onNext
      } else {
        val (use, keep) = buf.splitAt(Int.MaxValue)
        buf = keep
        use foreach onNext
        deliver()
      }
    }
}

object ClusterEventPublisher {
  def props(maxBufferSize: Int) = Props(classOf[ClusterEventPublisher], maxBufferSize)
}
