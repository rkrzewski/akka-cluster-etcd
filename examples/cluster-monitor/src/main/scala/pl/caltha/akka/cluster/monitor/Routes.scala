package pl.caltha.akka.cluster.monitor

import akka.stream.Materializer
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server.Directives._

trait Routes {

  implicit def executionContext: ExecutionContext

  implicit def materializer: Materializer

  def routes = path("health") {
    get {
      complete {
        "OK"
      }
    }
  }

}
