package pl.caltha.akka.streams

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Cancellable
import akka.stream.scaladsl.Flow
import akka.stream.stage.{AsyncCallback, AsyncContext, AsyncStage}

import scala.concurrent.{ExecutionContext, Promise}

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

  private object SameThreadExecutionContext extends ExecutionContext {
    override def execute(runnable: Runnable) = runnable.run()
    override def reportFailure(cause: Throwable) =
      throw new IllegalStateException("exception in SameThreadExecutionContext", cause)
  }

  private class FlowBreakerCancellable extends Cancellable {

    private val callbackPromise: Promise[AsyncCallback[FlowBreaker.Event]] = Promise()

    private val cancelled = new AtomicBoolean(false)

    def registerWith(callback: AsyncCallback[FlowBreaker.Event]) = callbackPromise.success(callback)

    override def cancel() = {
      val cancelling = cancelled.compareAndSet(false, true)
      if (cancelling) callbackPromise.future.map {_.invoke(CancelEvent)}(SameThreadExecutionContext)
      cancelling
    }

    override def isCancelled = cancelled.get()
  }


  private class FlowBreakerStage[Elem](cancellable: FlowBreakerCancellable)
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
