package pl.caltha.akka.etcd

case class EtcdNode(key: String, createdIndex: Int, modifiedIndex: Int, value: Option[String], dir: Option[Boolean], nodes: Option[List[EtcdNode]])

case class EtcdResponse(action: String, node: EtcdNode, prevNode: Option[EtcdNode])

case class EtcdError(errorCode: Int, message: String, cause: String, index: Int)