package pl.caltha.akka.etcd

import java.time.Month

import org.scalatest.{FlatSpec, Matchers}
import pl.caltha.akka.etcd.EtcdJsonProtocol._
import spray.json._

class EtcdJsonProtocolSpec extends FlatSpec with Matchers {

  "EtcdJsonProtocol" should "deserialize node with defined expiration time" in {
    val node = """{
      "createdIndex": 17,
      "dir": true,
      "expiration": "2013-12-11T10:37:33.689275857-08:00",
      "key": "/dir",
      "modifiedIndex": 17,
      "ttl": 30
    }""".parseJson.convertTo[EtcdNode]
    node.expiration match {
      case Some(i) ⇒
        i.getYear shouldBe 2013
        i.getMonth shouldBe Month.DECEMBER
        i.getDayOfMonth shouldBe 11
      case _ ⇒ fail
    }
  }

}
