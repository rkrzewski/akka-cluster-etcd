package pl.caltha.akka.etcd

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.pattern.pipe

/**
  * This actor will attempt the specified `operation` up to `retries` number of times, sends the
  * result back designated receiver actor and subsequently terminates.
  *
  * @param operation the operation that will be performed
  * @param replyTo the actor that will be informed about the outcome of the operation
  * @param etcd the EtcdClient instance to use
  * @param returnErrors if the operation yields an error response with `errorCode` in this list,
  *        the operation will not be retried, but `EtcdError` will be sent back immediately (this is useful
  *        for compare-and-swap type operations)
  * @param retryDelay the time to wait before retrying the operation
  * @param retries if the operation results `EtcdError` (except those in `returnErrors`) or timeout it
  *        will be retried up to `retries` number of times. When retries are exhausted, the result of last
  *        unsuccessful operation will be sent back. When negative argument is used, retries will be occur
  *        indefinitely.
  */
class EtcdOperationActor(operation: EtcdClient ⇒ Future[EtcdResponse], replyTo: ActorRef, etcd: EtcdClient,
                         returnErrors: Traversable[Int], retryDelay: FiniteDuration, retries: Int) extends Actor {

  import EtcdOperationActor._

  implicit val executionContext = context.system.dispatcher

  val receive = attempt(1)

  /**
    * Execute n-th attempt of the operation.
    */
  def attempt(num: Int): Receive = {

    // Send the request to `etcd` server and schedule a timeout event.
    operation(etcd).recover {
      case EtcdException(error) ⇒ error
    }.pipeTo(self)

    {
      case response: EtcdResponse ⇒
        reply(response)
      /* an error condition expected by the client (compareAndSwap operation, or similar) */
      case error @ EtcdError(code, _, _, _) if returnErrors.exists(code == _) ⇒
        reply(error)
      /* a timeout or unexpected error occurred, but more retries are allowed */
      case EtcdError(_, _, _, _) | Status.Failure(_) if retries < 0 || num <= retries ⇒
        context.system.scheduler.scheduleOnce(retryDelay, self, Retry)
      case Retry ⇒
        context.become(attempt(num + 1))
      /* timeout attempts exhausted */
      case error @ EtcdError(_, _, _, _) ⇒
        reply(error)
      case failure @ Status.Failure(_) ⇒
        reply(failure)
    }
  }

  /**
    * Send the message designated receiver and terminate.
    */
  def reply(message: Any) = {
    replyTo ! message
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
    * @param replyTo the actor that will be informed about the outcome of the operation
    * @param etcd the EtcdClient instance to use
    * @param returnErrors if the operation yields an error response with `errorCode` in this list,
    *        the operation will not be retried, but `EtcdError` will be sent back immediately (this is useful
    *        for compare-and-swap type operations)
    * @param retryDelay the time to wait before retrying the operation
    * @param retries if the operation results `EtcdError` (except those in `returnErrors`) or timeout it
    *        will be retried up to `retries` number of times. When retries are exhausted, the result of last
    *        unsuccessful operation will be sent back. When negative argument is used, retries will be occur
    *        indefinitely.
    * @param operation the operation that will be performed
    */
  def props(replyTo: ActorRef, etcd: EtcdClient, returnErrors: Traversable[Int],
            retryDelay: FiniteDuration, retries: Int)(operation: EtcdClient ⇒ Future[EtcdResponse]) =
    Props(classOf[EtcdOperationActor], operation, replyTo, etcd, returnErrors, retryDelay, retries)

  /** Message sent to self after retryDelay time elapses */
  private[EtcdOperationActor] object Retry
}
