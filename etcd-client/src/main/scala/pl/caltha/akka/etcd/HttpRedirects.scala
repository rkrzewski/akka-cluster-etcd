package pl.caltha.akka.etcd

import scala.collection.immutable.Set

import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.StatusCodes
import akka.http.model.headers.Location
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.FlowGraph
import akka.stream.scaladsl.Merge
import akka.stream.scaladsl.Zip

object HttpRedirects {

  def apply(client: Flow[HttpRequest, HttpResponse, _]): Flow[HttpRequest, HttpResponse, Unit] = {

    val redirectStatuses = Set(301, 302, 307)

    Flow[HttpRequest, HttpResponse]() {
      implicit b =>
        import FlowGraph.Implicits._

        val bc = b.add(Broadcast[HttpRequest](2))
        val zip = b.add(Zip[HttpRequest, HttpResponse]())
        val originalClient = b.add(client)
        val decider1 = b.add(EitherJunction[(HttpRequest, HttpResponse), HttpRequest, HttpResponse] {
          case (request, response) =>
            if (redirectStatuses.contains(response.status.intValue()) && response.header[Location].isDefined)
              Left(response.header[Location].map(location => request.withUri(location.uri)).get)
            else
              Right(response)
        })
        val redirectClient = b.add(client)
        val decider2 = b.add(EitherJunction[HttpResponse, HttpResponse, HttpResponse] {
          response =>
            if(redirectStatuses.contains(response.status.intValue()))
              Left(HttpResponse(StatusCodes.custom(310, "Too many redirects")))
            else
              Right(response)
        })
        val merge = b.add(Merge[HttpResponse](3))

        bc ~> zip.in0
        bc ~> originalClient ~> zip.in1
        zip.out ~> decider1.in
        decider1.left ~> redirectClient ~> decider2.in
        decider1.right ~> merge
        decider2.left ~> merge
        decider2.right ~> merge

        (bc.in, merge.out)
    }
  }
}