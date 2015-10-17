package pl.caltha.akka.etcd

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import akka.actor.Actor
import akka.pattern.pipe
import akka.actor.Props

/**
 * This actor will attempt the specified `operation` up to `retries` number of times, sends the
 * result back to it parent actor and subsequently terminates.
 *
 * @param etcd the EtcdClient instance to use
 * @param operation the operation that will be performed
 * @param returnErrors if the operation yields an error response with `errorCode` in this list,
 *        the operation will not be retried, but `EtcdError` will be sent back immediately (this is useful
 *        for compare-and-swap type operations)
 * @param timeout if `etcd` server fails to reply within the specified time, the operation will be retried.
 * @param retryDelay the time to wait before retrying the operation
 * @param retries if the operation results `EtcdError` (except those in `returnErrors`) or timeout it
 *        will be retried up to `retries` number of times. When retries are exhausted, the result of last
 *        unsuccessful operation will be sent back.
 */
class EtcdOperationActor(operation: EtcdClient ⇒ Future[EtcdResponse], etcd: EtcdClient,
    returnErrors: Traversable[Int], timeout: FiniteDuration, retryDelay: FiniteDuration, retries: Int) extends Actor {

  import EtcdOperationActor._

  implicit val executionContext = context.system.dispatcher

  /**
   * Handle the responses, starting with maximum allowed reply count.
   */
  val receive = attempt(retries)

  /**
   * Handle the incoming events.
   */
  def attempt(remaining: Int): Receive = {

    // Send the request to `etcd` server and schedule a timeout event.
    operation(etcd).recover {
      case EtcdException(error) ⇒ error
    }.pipeTo(self)

    context.system.scheduler.scheduleOnce(timeout, self, EtcdTimeout)

    {
      case response: EtcdResponse ⇒
        reply(response)
      /* an error condition expected by the client (compareAndSwap operation, or similar) */
      case error @ EtcdError(code, _, _, _) if returnErrors.exists(code == _) ⇒
        reply(error)
      /* a timeout or unexpected error occurred, but more retries are allowed */
      case _ if remaining > 0 ⇒
        context.system.scheduler.scheduleOnce(retryDelay, self, Retry)
      case Retry ⇒
        context.become(attempt(remaining - 1))
      /* timeout attempts exhausted */
      case resp: EtcdMessage ⇒
        reply(resp)
    }
  }

  /**
   * Send the message back to the Actor's parent and terminate.
   */
  def reply(message: EtcdMessage) = {
    context.parent ! message
    context.stop(self)
  }
}

/**
 * A factory for `EtcdOperationActor` `Props`.
 */
object EtcdOperationActor {

  /**
   * Create `akka.actor.Props` needed to instantiate `EtcdOperationActor`
   *
   * @param etcd the EtcdClient instance to use
   * @param returnErrors if the operation yields an error response with `errorCode` in this list,
   *        the operation will not be retried, but `EtcdError` will be sent back immediately (this is useful
   *        for compare-and-swap type operations)
   * @param timeout if `etcd` server fails to reply within the specified time, the operation will be retried.
   * @param retryDelay the time to wait before retrying the operation   *
   * @param retries if the operation results `EtcdError` (except those in `returnErrors`) or timeout it
   *        will be retried up to `retries` number of times. When retries are exhausted, the result of last
   *        unsuccessful operation will be sent back.
   * @param operation the operation that will be performed
   */
  def props(etcd: EtcdClient, returnErrors: Traversable[Int], timeout: FiniteDuration,
    retryDelay: FiniteDuration, retries: Int)(operation: EtcdClient ⇒ Future[EtcdResponse]) =
    Props(classOf[EtcdOperationActor], operation, etcd, returnErrors, timeout, retries)

  /** Message sent to self after retryDelay time elapses */
  private[EtcdOperationActor] object Retry
}
