package pl.caltha.akka.etcd

import spray.json._

object EtcdJsonProtocol extends DefaultJsonProtocol {

  implicit val etcNodeFormat: JsonFormat[EtcdNode] = lazyFormat(jsonFormat6(EtcdNode))

  implicit val etcdResponseFormat = jsonFormat3(EtcdResponse)

  implicit val etcdErrorFormat = jsonFormat4(EtcdError)

}