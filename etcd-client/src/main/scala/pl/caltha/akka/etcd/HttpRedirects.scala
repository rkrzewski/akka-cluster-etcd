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
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Zip
import akka.stream.scaladsl.Unzip

/**
 * HTTP redirects support for Akka HTTP Client
 */
object HttpRedirects {
  /**
   * Augments client flow with the capability to follow HTTP redirects up to a specified depth.
   *
   * {{{
   *                                                                 +----------------+
   *                          +--------------------------------------|redirectJunction|--->>>
   *                          |                                      +----------------+
   *                          |                                               ^
   *                          |                                               |
   *       +----------+       |                                         +----------+
   *       |origReqNum|       |                +----------------------->|reqRespZip|
   *       +----------+       |                |                        +----------+
   *            |             |                |                              ^
   *            v             v                |                              |
   *       +----------+   +--------+   +---------------+   +----------+   +--------+
   * >>>---|origReqZip|-->|reqMerge|-->|reqNumBroadcast|-->|extractReq|-->|  http  |
   *       +----------+   +--------+   +---------------+   +----------+   +--------+
   * }}}
   * @param httpFlow the request to response flow that will be used to contact the server.
   * @param maxRedirects maximum number of redirects that will be accepted. When server returns a
   *        redirect code for the final request, processing will be terminated with `310 Too many
   *        redirects` response.
   * @param redirectStatuses HTTP status codes that will be recognized as redirects. The response must contain
   *        `Location` header, otherwise the response will be passed as-is to the client.
   * @returns augmented request to response flow.
   */
  def apply(httpFlow: Flow[HttpRequest, HttpResponse, _], maxRedirects: Int, redirectStatuses: Set[Int] = Set(301, 302, 307)): Flow[HttpRequest, HttpResponse, Unit] =
    Flow[HttpRequest, HttpResponse]() { implicit b =>
      import FlowGraph.Implicits._

      val origReqNum = b.add(Source.repeat(0))
      val origReqZip = b.add(Zip[HttpRequest, Int]())
      val reqMerge = b.add(Merge[(HttpRequest, Int)](2))
      val reqNumBroadcast = b.add(Broadcast[(HttpRequest, Int)](2))
      val extractReq = b.add(Flow[(HttpRequest, Int)].map { case (req, _) => req })
      val http = b.add(httpFlow)
      val reqRespZip = b.add(Zip[(HttpRequest, Int), HttpResponse]())
      val redirectJunction = b.add(EitherJunction[((HttpRequest, Int), HttpResponse), (HttpRequest, Int), HttpResponse] {
        case ((request, requestNum), response) =>
          if (redirectStatuses.contains(response.status.intValue()) && response.header[Location].isDefined)
            if (requestNum < maxRedirects)
              Left((response.header[Location].map(location => request.withUri(location.uri)).get, requestNum + 1))
            else
              Right(HttpResponse(StatusCodes.custom(310, "Too many redirects")))
          else
            Right(response)
      })

      // in ~> origReqZip.in0
      origReqNum ~> origReqZip.in1
      origReqZip.out ~> reqMerge.in(0)
      reqMerge ~> reqNumBroadcast
      reqNumBroadcast.out(1) ~> reqRespZip.in0
      reqNumBroadcast.out(0) ~> extractReq.inlet
      extractReq ~> http ~> reqRespZip.in1
      reqRespZip.out ~> redirectJunction.in
      redirectJunction.left ~> reqMerge.in(1)
      // redirectJunction.right ~> out

      (origReqZip.in0, redirectJunction.right)
    }
}