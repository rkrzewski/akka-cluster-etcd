package pl.caltha.akka.etcd

import spray.json._

/**
 * Provides Spray JSON format implicits for `etcd` messages.
 */
object EtcdJsonProtocol extends DefaultJsonProtocol {

  /** Spray JSON format for [[EtcdNode]] case class. */
  implicit val etcNodeFormat: JsonFormat[EtcdNode] = lazyFormat(jsonFormat6(EtcdNode))

  /** Spray JSON format for [[EtcdResponse]] case class. */
  implicit val etcdResponseFormat = jsonFormat3(EtcdResponse)

  /** Spray JSON format for [[EtcdError]] case class. */
  implicit val etcdErrorFormat = jsonFormat4(EtcdError.apply)

}