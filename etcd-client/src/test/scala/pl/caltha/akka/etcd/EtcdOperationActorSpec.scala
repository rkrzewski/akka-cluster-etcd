package pl.caltha.akka.etcd

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FlatSpecLike
import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar
import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import akka.stream.StreamTcpException

class EtcdOperationActorSpec(_system: ActorSystem) extends TestKit(_system)
    with FlatSpecLike with BeforeAndAfterAll with Matchers with MockitoSugar {

  def this() =
    this(ActorSystem("testsystem"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait Fixture {

    val etcd = mock[EtcdClient]

    val probe = TestProbe()

    def run(returnErrors: Traversable[Int], retries: Int)(operation: EtcdClient ⇒ Future[EtcdResponse]) =
      system.actorOf(EtcdOperationActor.props(probe.ref, etcd, returnErrors, 100.milliseconds, retries)(operation))
  }

  "EtcdOperationActor" should "return results of succesful operations" in new Fixture {
    val promise = Promise[EtcdResponse]
    when(etcd.get("/key", false, false)).thenReturn(promise.future)
    val actor = run(Seq(), 0) { etcd ⇒
      etcd.get("/key", false, false)
    }
    val reply = EtcdResponse("get", EtcdNode("/key", 100, 100, None, Some("value"), None, None), None)
    probe.watch(actor)
    promise.success(reply)
    probe.expectMsg(reply)
    probe.expectTerminated(actor, 1.second)
  }

  it should "return expected error results imediately" in new Fixture {
    val promise = Promise[EtcdResponse]
    when(etcd.compareAndSet("/key", "value1", None, Some("value0"), None, None)).thenReturn(promise.future)
    val actor = run(Seq(EtcdError.TestFailed), 1) { etcd ⇒
      etcd.compareAndSet("/key", "value1", None, Some("value0"), None, None)
    }
    val reply = EtcdError(EtcdError.TestFailed, "Compare failed", "value2 != value1", 120)
    probe.watch(actor)
    promise.failure(EtcdException(reply))
    probe.expectMsg(reply)
    probe.expectTerminated(actor, 1.second)
  }

  it should "retry operations that yield unexpected errors" in new Fixture {
    val promise1 = Promise[EtcdResponse]
    val promise2 = Promise[EtcdResponse]
    when(etcd.get("/key", false, false)).thenReturn(promise1.future).thenReturn(promise2.future)
    val actor = run(Seq(), 1) { etcd ⇒
      etcd.get("/key", false, false)
    }
    probe.watch(actor)
    promise1.failure(EtcdException(EtcdError(EtcdError.RaftInternal, "", "", 100)))
    val reply = EtcdResponse("get", EtcdNode("/key", 100, 100, None, Some("value"), None, None), None)
    promise2.success(reply)
    probe.expectMsg(reply)
    probe.expectTerminated(actor, 1.second)
  }

  it should "retry operations that fail with exceptions" in new Fixture {
    val promise1 = Promise[EtcdResponse]
    val promise2 = Promise[EtcdResponse]
    when(etcd.get("/key", false, false)).thenReturn(promise1.future).thenReturn(promise2.future)
    val actor = run(Seq(), 1) { etcd ⇒
      etcd.get("/key", false, false)
    }
    probe.watch(actor)
    promise1.failure(new StreamTcpException("Connection failed"))
    val reply = EtcdResponse("get", EtcdNode("/key", 100, 100, None, Some("value"), None, None), None)
    promise2.success(reply)
    probe.expectMsg(reply)
    probe.expectTerminated(actor, 1.second)
  }

  it should "retry specified number of times" in new Fixture {
    val promises = for {
      _ ← (1 to 9)
    } yield Promise[EtcdResponse]
    val finalPromise = Promise[EtcdResponse]
    val stub = when(etcd.get("/key", false, false))
    promises.foldLeft(stub) { case (stub, p) ⇒ stub.thenReturn(p.future) }.thenReturn(finalPromise.future)
    val actor = run(Seq(), 10) { etcd ⇒
      etcd.get("/key", false, false)
    }
    probe.watch(actor)
    promises.foreach { p ⇒ p.failure(new StreamTcpException("Connection failed")) }
    val reply = EtcdResponse("get", EtcdNode("/key", 100, 100, None, Some("value"), None, None), None)
    finalPromise.success(reply)
    probe.expectMsg(reply)
    probe.expectTerminated(actor, 1.second)
  }

  it should "keep retring if negative retry count is provided" in new Fixture {
    when(etcd.get("/key", false, false)).thenReturn(Future.failed(new StreamTcpException("Connection failed")))
    val actor = run(Seq(), -1) { etcd ⇒
      etcd.get("/key", false, false)
    }
    probe.expectNoMsg(1.second)
  }
}
