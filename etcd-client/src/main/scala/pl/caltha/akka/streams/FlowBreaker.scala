package pl.caltha.akka.streams

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Cancellable
import akka.stream.{Outlet, Inlet, Attributes, FlowShape}
import akka.stream.scaladsl.Flow
import akka.stream.stage._

import scala.concurrent.{ExecutionContext, Promise}

/**
  * Flow element with a materialized value of type `Cancellable` that allows
  * cancelling upstream and completing downstream flow on demand.
  */
object FlowBreaker {
  def apply[T]: Flow[T, T, Cancellable] = Flow.fromGraph(new FlowBreakerStage[T])


  private case object CancelEvent

  private object SameThreadExecutionContext extends ExecutionContext {
    override def execute(runnable: Runnable) = runnable.run()
    override def reportFailure(cause: Throwable) =
      throw new IllegalStateException("exception in SameThreadExecutionContext", cause)
  }

  private class FlowBreakerCancellable extends Cancellable {
    private val callbackPromise: Promise[AsyncCallback[CancelEvent.type]] = Promise()
    private val cancelled = new AtomicBoolean(false)

    def registerWith(callback: AsyncCallback[CancelEvent.type]) = callbackPromise.success(callback)

    override def cancel() = {
      val cancelling = cancelled.compareAndSet(false, true)
      if (cancelling) callbackPromise.future.map {_.invoke(CancelEvent)}(SameThreadExecutionContext)
      cancelling
    }

    override def isCancelled = cancelled.get()
  }

  private class FlowBreakerStage[Elem] extends GraphStageWithMaterializedValue[FlowShape[Elem, Elem], Cancellable] {

    val in = Inlet[Elem]("in")
    val out = Outlet[Elem]("out")

    override val shape = FlowShape(in, out)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
      val cancellable = new FlowBreakerCancellable
      val logic = new GraphStageLogic(shape) {
        override def preStart() = {
          val callback = getAsyncCallback[CancelEvent.type] { _ => completeStage() }
          cancellable.registerWith(callback)
          pull(in)
        }

        setHandler(in, eagerTerminateInput)
        setHandler(out, eagerTerminateOutput)
      }

      (logic, cancellable)
    }
  }

}
