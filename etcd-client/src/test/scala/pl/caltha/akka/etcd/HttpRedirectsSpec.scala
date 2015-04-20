package pl.caltha.akka.etcd

import scala.concurrent.Future

import org.scalatest.Finders
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures

import akka.actor.ActorSystem
import akka.http.marshalling._
import akka.http.model.HttpMethods.GET
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.StatusCodes
import akka.http.model.headers.Location
import akka.http.server.Directives._
import akka.http.server.Route
import akka.http.server.RoutingSettings
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

class HttpRedirectsSpec extends FlatSpec with Matchers with ScalaFutures {

  implicit val system = ActorSystem()

  implicit val executionContext = system.dispatcher

  implicit val flowMaterializer = ActorFlowMaterializer()

  implicit val routingSettings = RoutingSettings.default(system)

  def mockServerFlow(numRedicects: Int): Flow[HttpRequest, HttpResponse, Unit] = {
    val route = get {
      pathPrefix(IntNumber) { num =>
        if (num < numRedicects)
          overrideStatusCode(StatusCodes.TemporaryRedirect) {
            respondWithHeader(Location(s"/${num + 1}")) {
              complete {
                "redirect"
              }
            }
          }
        else {
          complete {
            "ok"
          }
        }
      }
    }
    Route.handlerFlow(route)
  }

  def request(serverFlow: Flow[HttpRequest, HttpResponse, Unit]): Future[HttpResponse] =
    Source.single(HttpRequest(GET).withUri("/0")).via(serverFlow).runWith(Sink.head)

  "redirect-enabled client" should "handle direct response" in {
    val flow = HttpRedirects.apply(mockServerFlow(numRedicects = 0))
    whenReady(request(flow)) {
      resp =>
        resp.status shouldBe StatusCodes.OK
    }
  }

  it should "handle single redirect" in {
    val flow = HttpRedirects.apply(mockServerFlow(numRedicects = 1))
    whenReady(request(flow)) {
      resp =>
        resp.status shouldBe StatusCodes.OK
    }
  }

  it should "report `Too many redirects` on double redirect" in {
    val flow = HttpRedirects.apply(mockServerFlow(numRedicects = 2))
    whenReady(request(flow)) {
      resp =>
        resp.status.intValue() shouldBe 310
    }
  }
}