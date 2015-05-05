package pl.caltha.akka.cluster

import scala.concurrent.duration.FiniteDuration

/**
 * Settings for `etcd` base cluster discovery.
 *
 * @param etcdHost host where `etcd` server is listening.
 * @param etcdPort port where `etcd` server is listening.
 * @param etcdPath base path within `etcd` key space where cluster discovery should store it's data.
 */
case class ClusterDiscoverySettings(
    etcdHost: String,
    etcdPort: Int,
    etcdPath: String,
    etcdTimeout: FiniteDuration,
    seedNodesFetchTimeout: FiniteDuration,
    leaderEntryTTL: FiniteDuration) {

  import ClusterDiscoverySettings._

  val leaderPath: String =
    s"${etcdPath}$LeaderPath"

  val seedsPath =
    s"${etcdPath}$SeedsPath"
}

object ClusterDiscoverySettings {

  val LeaderPath = "/leader"

  /**
   * Path, relative to [[etcdPath]], where seed nodes information is stored
   */
  val SeedsPath = "/seeds"

}