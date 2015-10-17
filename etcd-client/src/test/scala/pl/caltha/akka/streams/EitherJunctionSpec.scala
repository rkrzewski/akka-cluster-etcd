package pl.caltha.akka.streams

import scala.Left
import scala.Right
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import akka.stream.scaladsl.FlowGraph
import akka.stream.scaladsl.Merge
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Zip

class EitherJunctionSpec extends FlatSpec with ScalaFutures with Matchers {

  "EitherJunction" should "route inputs properly" in {
    val stream = Source() { implicit b ⇒
      import FlowGraph.Implicits._

      val source = b.add(Source(1 to 10))
      val either = b.add(EitherJunction[Int, Int, Int] { i ⇒
        if (i <= 5) Left(i)
        else Right(i)
      })
      val low = b.add(Source.repeat("L"))
      val high = b.add(Source.repeat("H"))
      val zipLow = b.add(Zip[Int, String]())
      val zipHigh = b.add(Zip[Int, String]())
      val merge = b.add(Merge[(Int, String)](2))

      source ~> either.in

      either.left ~> zipLow.in0
      low ~> zipLow.in1
      zipLow.out ~> merge.in(0)

      either.right ~> zipHigh.in0
      high ~> zipHigh.in1
      zipHigh.out ~> merge.in(1)

      merge.out
    }

    implicit val system = ActorSystem()

    implicit val executionContext = system.dispatcher

    implicit val Materializer: Materializer = ActorMaterializer()

    whenReady(stream.runWith(Sink.fold(Seq[(Int, String)]())((seq, elem) ⇒ elem +: seq))) { result ⇒
      result.forall { case (i, s) ⇒ if (i <= 5) s == "L" else s == "H" } shouldBe true
    }
  }
}