package pl.caltha.akka.etcd

import org.scalatest.Finders
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import akka.actor.ActorSystem
import akka.http.marshalling.PredefinedToEntityMarshallers.StringMarshaller
import akka.http.marshalling.ToResponseMarshallable.apply
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
import scala.concurrent.Future

class HttpRedirectsSpec extends FlatSpec with Matchers with ScalaFutures {

  implicit val system = ActorSystem()

  implicit val executionContext = system.dispatcher

  implicit val flowMaterializer = ActorFlowMaterializer()

  def mockServerFlow(numRedicects: Int): Flow[HttpRequest, HttpResponse, Unit] = {
    val route = get {
      pathPrefix(IntNumber) { num =>
        if (num < numRedicects)
          overrideStatusCode(StatusCodes.TemporaryRedirect) {
            respondWithHeader(Location(s"/${num+1}")) {
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
    implicit val routingSettings = RoutingSettings.default(system)

    Route.handlerFlow(route)
  }

  def request(n: Int, serverFlow: Flow[HttpRequest, HttpResponse, Unit]): Future[HttpResponse] =
    Source.single(HttpRequest(GET).withUri(s"/$n")).via(serverFlow).runWith(Sink.head)

  "redirect-enabled client" should "handle direct response" in {
    val flow = HttpRedirects.apply(mockServerFlow(0))
    whenReady(request(0, flow)) {
      resp =>
        resp.status shouldBe StatusCodes.OK
    }
  }
  
 it should "handle single redirect" in {
    val flow = HttpRedirects.apply(mockServerFlow(1))
    whenReady(request(0, flow)) {
      resp =>
        resp.status shouldBe StatusCodes.OK
    }
  }
 
 it should "report `Too many redirects` on double redirect" in {
   val flow = HttpRedirects.apply(mockServerFlow(2))
    whenReady(request(0, flow)) {
      resp =>
        resp.status.intValue() shouldBe 310
    }
 }

}