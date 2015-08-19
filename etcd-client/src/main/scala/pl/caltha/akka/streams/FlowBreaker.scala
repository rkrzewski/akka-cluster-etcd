package pl.caltha.akka.streams

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Cancellable
import akka.stream.scaladsl.Flow

/**
 * Flow element with a materialized value of type `Cancellable` that allows
 * cancelling upstream and completing downstream flow on demand.
 */
object FlowBreaker {
  def apply[T]: Flow[T, T, Cancellable] = {
    val cancellable = new Cancellable {
      private val cancelled = new AtomicBoolean()
      override def cancel() = cancelled.compareAndSet(false, true)
      override def isCancelled() = cancelled.get
    }
    Flow[T].takeWhile(_ => !cancellable.isCancelled).mapMaterializedValue(_ => cancellable)
  }
}