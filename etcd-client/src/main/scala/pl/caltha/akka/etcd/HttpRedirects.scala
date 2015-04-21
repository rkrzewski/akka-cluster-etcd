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
   *                                   requestNum < maxRedirects +---------------+ 310
   *                          +----------------------------------|nextReqJunction|--------+
   *                          |                                  +---------------+        |
   *                          |                                          ^                v
   *                          |                                   +----------- +    +-----------+
   *                          |            +--------------------->| nextReqZip |    | respMerge |--->>>
   *                          |            |                      +------------+    +-----------+
   *                          |            |                             ^ 30x            ^
   *     +--------------+     |            |                    +----------------+ 200    |
   *     |origReqNum = 0|     |            |                    |redirectJunction|--------+
   *     +--------------+     |            |                    +----------------+
   *            v             v            |                            ^
   *       +----------+   +--------+   +--------+                  +----------+
   * >>>---|origReqZip|-->|reqMerge|-->|reqUnzip|        +-------->|reqRespZip|
   *       +----------+   +--------+   +--------+        |         +----------+
   *                                       |             |              ^
   *                                       |       +------------+    +------+
   *                                       +------>|reqBroadcast|--->| http |
   *                                               +------------+    +------+
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
      val reqUnzip = b.add(Unzip[HttpRequest, Int]())
      val reqBroadcast = b.add(Broadcast[HttpRequest](2))
      val http = b.add(httpFlow)
      val reqRespZip = b.add(Zip[HttpRequest, HttpResponse]())
      val redirectJunction = b.add(EitherJunction[(HttpRequest, HttpResponse), HttpRequest, HttpResponse] {
        case (request, response) =>
          if (redirectStatuses.contains(response.status.intValue()) && response.header[Location].isDefined)
            Left(response.header[Location].map(location => request.withUri(location.uri)).get)
          else
            Right(response)
      })
      val nextReqZip = b.add(Zip[HttpRequest, Int]())
      val nextReqJunction = b.add(EitherJunction[(HttpRequest, Int), HttpResponse, (HttpRequest, Int)] {
        case (request, requestNum) =>
          if (requestNum < maxRedirects)
            Right((request, requestNum + 1))
          else
            Left(HttpResponse(StatusCodes.custom(310, "Too many redirects")))
      })
      val respMerge = b.add(Merge[HttpResponse](2))

      // in ~> origReqZip.in0
      origReqNum ~> origReqZip.in1
      origReqZip.out ~> reqMerge.in(0)
      reqMerge.out ~> reqUnzip.in
      reqUnzip.out0 ~> reqBroadcast.in
      reqBroadcast.out(0) ~> http.inlet
      reqBroadcast.out(1) ~> reqRespZip.in0
      http.outlet ~> reqRespZip.in1
      reqRespZip.out ~> redirectJunction.in
      redirectJunction.left ~> nextReqZip.in0
      reqUnzip.out1 ~> nextReqZip.in1
      redirectJunction.right ~> respMerge.in(0)
      nextReqZip.out ~> nextReqJunction.in
      nextReqJunction.left ~> respMerge.in(1)
      nextReqJunction.right ~> reqMerge.in(1)
      // reqMerge.out ~> out

      (origReqZip.in0, respMerge.out)
    }
}