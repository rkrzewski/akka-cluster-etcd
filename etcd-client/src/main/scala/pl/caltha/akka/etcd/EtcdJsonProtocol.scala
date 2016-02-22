package pl.caltha.akka.etcd

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import spray.json._

/**
  * Provides Spray JSON format implicits for `etcd` messages.
  */
object EtcdJsonProtocol extends DefaultJsonProtocol {

  /** Spray JSON format for [[EtcdError]] case class. */
  implicit val etcdErrorFormat = jsonFormat4(EtcdError.apply)

  /** Spray JSON format for `java.time.ZonedDateTime` represented as `ISO_ZONED_DATE_TIME` string*/
  implicit object InstantJsonFormat extends RootJsonFormat[ZonedDateTime] {
    val formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

    def write(i: ZonedDateTime) =
      JsString(formatter.format(i))

    def read(value: JsValue) = value match {
      case JsString(s) ⇒ ZonedDateTime.from(formatter.parse(s))
      case _ ⇒ deserializationError("string expected")
    }
  }

  /** Spray JSON format for [[EtcdNode]] case class. */
  implicit val etcNodeFormat: JsonFormat[EtcdNode] = lazyFormat(jsonFormat7(EtcdNode))

  /** Spray JSON format for [[EtcdResponse]] case class. */
  implicit val etcdResponseFormat = jsonFormat3(EtcdResponse)
}