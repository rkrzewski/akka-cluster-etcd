package pl.caltha.akka.streams

import akka.actor.{ActorSystem, Cancellable}
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.{TestKit, TestKitBase}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.duration._

class FlowBreakerSpec extends FlatSpec with Matchers with TestKitBase with BeforeAndAfterAll {

  override implicit lazy val system: ActorSystem = ActorSystem("FlowBreakerSpec")
  implicit val mat: Materializer = ActorMaterializer()

  def makeBreakable[T](source: Source[T, _]): Source[T, Cancellable] = source.viaMat(FlowBreaker[T])(Keep.right)

  "FlowBreaker when canceled" should "complete a running stream of elements" in {
    val source = makeBreakable(Source.repeat(42))

    val sink = TestSink.probe[Int]
    val (cancellable, probe) = source.toMat(sink)(Keep.both).run()

    cancellable.cancel()
    probe.request(1)
    probe.expectComplete()
  }

  "FlowBreaker when canceled" should "complete immediately even without upstream elements" in {
    val source = makeBreakable(Source.maybe[Int])

    val sink = TestSink.probe[Int]
    val (cancellable, probe) = source.toMat(sink)(Keep.both).run()

    cancellable.cancel()
    probe.request(1)
    probe.within(1.second) {
      probe.expectComplete()
    }
  }

  override def afterAll() = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

}
