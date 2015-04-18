package pl.caltha.akka.etcd

import scala.concurrent.Future

import akka.http.Http
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.headers.Location
import akka.stream.FanOutShape
import akka.stream.FanOutShape.Init
import akka.stream.FanOutShape.Name
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.FlexiRoute
import akka.stream.scaladsl.FlexiRoute.DemandFromAll
import akka.stream.scaladsl.FlexiRoute.RouteLogic
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.FlowGraph
import akka.stream.scaladsl.Merge
import akka.stream.scaladsl.OperationAttributes
import akka.stream.scaladsl.Zip

object HttpRedirects {
  def apply(client: Flow[HttpRequest, HttpResponse, _]): Flow[HttpRequest, HttpResponse, Unit] = {

    class RedirectDeciderShape(_init: Init[(HttpRequest, HttpResponse)] = Name[(HttpRequest, HttpResponse)]("RedirectDecider"))
        extends FanOutShape[(HttpRequest, HttpResponse)](_init) {
      val redirect = newOutlet[HttpRequest]("redirect")
      val response = newOutlet[HttpResponse]("response")
      protected override def construct(i: Init[(HttpRequest, HttpResponse)]) = new RedirectDeciderShape(i)
    }

    class RedirectDecider extends FlexiRoute[(HttpRequest, HttpResponse), RedirectDeciderShape](
      new RedirectDeciderShape, OperationAttributes.name("RedirectDecider")) {
      import FlexiRoute._

      val redirectStatuses = Set(301, 302, 307)

      def redirect(req: HttpRequest): HttpRequest =
        req.header[Location].map(location => req.withUri(location.uri)).get

      override def createRouteLogic(p: PortT) = new RouteLogic[(HttpRequest, HttpResponse)] {
        override def initialState = State[Any](DemandFromAll(p.redirect, p.response)) {
          (ctx, _, elements) =>
            val (request, response) = elements
            if (redirectStatuses.contains(response.status.intValue()) &&
              response.header[Location].isDefined) {
              ctx.emit(p.redirect)(redirect(request))
            } else {
              ctx.emit(p.response)(response)
            }
            SameState
        }

        override def initialCompletionHandling = eagerClose
      }
    }

    Flow[HttpRequest, HttpResponse]() {
      implicit b =>
        import FlowGraph.Implicits._

        val bc = b.add(Broadcast[HttpRequest](2))
        val zip = b.add(Zip[HttpRequest, HttpResponse]())
        val originalClient = b.add(client)
        val decider = b.add(new RedirectDecider)
        val redirectClient = b.add(client)
        val merge = b.add(Merge[HttpResponse](2))

        bc ~> zip.in0
        bc ~> originalClient ~> zip.in1
        zip.out ~> decider.in
        decider.response ~> merge
        decider.redirect ~> redirectClient ~> merge

        (bc.in, merge.out)
    }
  }
}