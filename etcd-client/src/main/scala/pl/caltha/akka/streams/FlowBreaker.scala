package pl.caltha.akka.streams

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Cancellable
import akka.stream.scaladsl.Flow
import akka.stream.stage.{AsyncCallback, AsyncContext, AsyncStage}

/**
 * Flow element with a materialized value of type `Cancellable` that allows
 * cancelling upstream and completing downstream flow on demand.
 */
object FlowBreaker {
  def apply[T]: Flow[T, T, Cancellable] = {
    val cancellable = new FlowBreakerCancellable
    Flow[T].transform(() => new FlowBreakerStage[T](cancellable)).mapMaterializedValue(_ => cancellable)
  }

  sealed trait Event
  case object CancelEvent extends Event

  class FlowBreakerCancellable extends Cancellable {

    private var callback: AsyncCallback[FlowBreaker.Event] = _
    private val cancelled = new AtomicBoolean(false)

    def registerWith(callback: AsyncCallback[FlowBreaker.Event]) = this.callback = callback

    override def cancel() = {
      val cancelling = cancelled.compareAndSet(false, true)
      if (cancelling) callback.invoke(CancelEvent)
      cancelling
    }

    override def isCancelled = cancelled.get()
  }


  class FlowBreakerStage[Elem](cancellable: FlowBreakerCancellable)
    extends AsyncStage[Elem, Elem, Event] {

    private var inFlight: Option[Elem] = None

    override def preStart(ctx: AsyncContext[Elem, Event]): Unit = {
      cancellable.registerWith(ctx.getAsyncCallback())
    }

    override def onAsyncInput(event: Event, ctx: AsyncContext[Elem, Event]) = {
      ctx.finish()
    }

    override def onPush(elem: Elem, ctx: AsyncContext[Elem, Event]) = {
      if (ctx.isHoldingDownstream) ctx.pushAndPull(elem)
      else {
        inFlight = Some(elem)
        ctx.holdUpstream()
      }
    }

    override def onPull(ctx: AsyncContext[Elem, Event]) = {
      if (inFlight.isEmpty) {
        if (ctx.isFinishing) ctx.finish()
        else ctx.holdDownstream()
      } else {
        val next = inFlight.get
        if (ctx.isHoldingUpstream) ctx.pushAndPull(next)
        else ctx.push(next)
      }
    }


    override def onUpstreamFinish(ctx: AsyncContext[Elem, Event]) = inFlight match {
      case Some(_) => ctx.absorbTermination()
      case None => ctx.finish()
    }

  }

}
