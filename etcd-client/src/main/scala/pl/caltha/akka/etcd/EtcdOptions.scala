package pl.caltha.akka.etcd

sealed trait EtcdOption

case object Consistent extends EtcdOption

case object Quorum extends EtcdOption