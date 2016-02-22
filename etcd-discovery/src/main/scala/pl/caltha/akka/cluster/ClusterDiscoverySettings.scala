package pl.caltha.akka.cluster

import scala.concurrent.duration.FiniteDuration
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

/**
  * Settings for `etcd` base cluster discovery.
  *
  * @param etcdHost host where `etcd` server is listening.
  * @param etcdPort port where `etcd` server is listening.
  * @param etcdPath base path within `etcd` key space where cluster discovery should store it's data.
  * @param etcdConnectionTimeout Timeout for connecting to `etcd`.
  * @param etcdRequestTimeout Timeout for HTTP requests to `etcd` server.
  * @param etcdRetryDelay Time to wait before retrying failed `etcd` operations.
  * @param seedsFetchTimeout Timeout for waiting for seed nodes to be published after losing leader election.
  *         Election will be retired after this time passes.
  * @param seedsJoinTimeout Timeout for attempting to join the cluster using seed node addresses fetched from
  *         etcd. If none of the seeds can be contacted during specified time, election will be retried.
  * @param leaderEntryTTL TTL for leader entry in `etcd`. Leader will attempt to refresh twice during that period.
  */
case class ClusterDiscoverySettings(
    etcdHost:              String,
    etcdPort:              Int,
    etcdPath:              String,
    etcdConnectionTimeout: FiniteDuration,
    etcdRequestTimeout:    FiniteDuration,
    etcdRetryDelay:        FiniteDuration,
    seedsFetchTimeout:     FiniteDuration,
    seedsJoinTimeout:      FiniteDuration,
    leaderEntryTTL:        FiniteDuration) {

  import ClusterDiscoverySettings._

  val leaderPath: String =
    s"${etcdPath}$LeaderPath"

  val seedsPath =
    s"${etcdPath}$SeedsPath"
}

object ClusterDiscoverySettings {

  /**
    * Path, relative to `etcdPath`, where seed leader election takes place
    */
  val LeaderPath = "/leader"

  /**
    * Path, relative to `etcdPath`, where seed nodes information is stored
    */
  val SeedsPath = "/seeds"

  /**
    * Initialize settings from Typesafe Config object
    */
  def load(config: Config) = {

    val c = config.getConfig("akka.cluster.discovery.etcd")

    val t = c.getConfig("timeouts")

    def duration(path: String) =
      FiniteDuration(t.getDuration(path, TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

    ClusterDiscoverySettings(
      c.getString("host"),
      c.getInt("port"),
      c.getString("path"),
      duration("etcdConnection"),
      duration("etcdRequest"),
      duration("etcdRetry"),
      duration("seedsFetch"),
      duration("seedsJoin"),
      duration("leaderEntry"))
  }

}
