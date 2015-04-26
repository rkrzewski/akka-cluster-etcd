package pl.caltha.akka.streams

import scala.collection.immutable.Seq
import akka.stream.Inlet
import akka.stream.OperationAttributes
import akka.stream.Outlet
import akka.stream.Shape
import akka.stream.scaladsl.FlexiRoute
import akka.stream.scaladsl.FlexiRoute._

import EitherJunctionShape._

object EitherJunctionShape {
  sealed trait Init[I, L, R]
  case class Name[I, L, R](name: String) extends Init[I, L, R]
  case class Ports[I, L, R](in: Inlet[I], left: Outlet[L], right: Outlet[R]) extends Init[I, L, R]
}

class EitherJunctionShape[I, L, R](init: EitherJunctionShape.Init[I, L, R] = Name[I, L, R]("EitherJunction")) extends Shape {
  private val (_in, _left, _right) = init match {
    case Name(name) => (new Inlet[I](s"$name.in"), new Outlet[L](s"$name.left"), new Outlet[R](s"$name.right"))
    case Ports(in, left, right) => (in, left, right)
  }

  def in: Inlet[I] = _in
  def left: Outlet[L] = _left
  def right: Outlet[R] = _right

  def inlets: Seq[Inlet[_]] = Seq(_in)

  def outlets: Seq[Outlet[_]] = Seq(_left, _right)

  private def construct(in: Inlet[I], left: Outlet[L], rigth: Outlet[R]) =
    new EitherJunctionShape(Ports(in, left, right))

  def deepCopy(): Shape = {
    construct(new Inlet[I](_in.toString), new Outlet[L](_left.toString), new Outlet[R](_right.toString))
  }

  def copyFromPorts(inlets: Seq[Inlet[_]], outlets: Seq[Outlet[_]]): Shape = {
    require(inlets.size == 1, s"proposed inlets [${inlets.mkString(", ")}] do not fit EitherJunctionShape")
    require(outlets.size == 2, s"proposed outlets [${outlets.mkString(", ")}] do not fit EitherJunctionShape")
    construct(inlets(0).asInstanceOf[Inlet[I]], outlets(0).asInstanceOf[Outlet[L]], outlets(1).asInstanceOf[Outlet[R]])
  }
}

class EitherJunction[I, L, R](f: I => Either[L, R]) extends FlexiRoute[I, EitherJunctionShape[I, L, R]](new EitherJunctionShape, OperationAttributes.name("EitherJunction")) {
  override def createRouteLogic(p: PortT) = new RouteLogic[I] {
    override def initialState = State[Any](DemandFromAll(p.left, p.right)) {
      (ctx, _, element) =>
        f(element) match {
          case Left(l) => ctx.emit(p.left)(l)
          case Right(r) => ctx.emit(p.right)(r)
        }
        SameState
    }
    override def initialCompletionHandling = eagerClose
  }
}

object EitherJunction {
  def apply[I, L, R](f: I => Either[L, R]) = new EitherJunction(f)
}
