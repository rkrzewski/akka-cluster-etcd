package pl.caltha.akka.etcd

import scala.collection.immutable.Traversable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.ClientConnectionSettings
import akka.stream.scaladsl.Source

/**
 * `etcd` client API
 */
trait EtcdClient {

  def get(key: String, recursive: Boolean = false, sorted: Boolean = false): Future[EtcdResponse]

  def wait(key: String, waitIndex: Option[Int] = None, recursive: Boolean = false,
    sorted: Boolean = false, quorum: Boolean = false): Future[EtcdResponse]

  def set(key: String, value: String, ttl: Option[Int] = None): Future[EtcdResponse]

  def compareAndSet(key: String, value: String, ttl: Option[Int] = None,
    prevValue: Option[String] = None, prevIndex: Option[Int] = None,
    prevExist: Option[Boolean] = None): Future[EtcdResponse]
  
  def clearTTL(key: String): Future[EtcdResponse]

  def create(parentKey: String, value: String): Future[EtcdResponse]

  def createDir(key: String, ttl: Option[Int] = None): Future[EtcdResponse]

  def delete(key: String, recursive: Boolean = false): Future[EtcdResponse]

  def compareAndDelete(key: String, prevValue: Option[String] = None, prevIndex: Option[Int] = None): Future[EtcdResponse]

  def watch(key: String, waitIndex: Option[Int] = None, recursive: Boolean = false,
    quorum: Boolean = false): Source[EtcdResponse, Unit]

}

/**
 * `etcd` client factory.
 */
object EtcdClient {

  /**
   * Creates a new instance of `etcd` client.
   *
   * @param host host to connect to.
   * @param port port to connect to, 4001 by default.
   * @param socketOptions optional socket options for Akka IO.
   * @param httpClientSettings optional client options for Akka HTTP.
   * @param actorSystem the ActorSystem that will be used for materializing HTTP flows and asynchronous processing.
   */
  def apply(host: String, port: Int = 4001,
    httpClientSettings: Option[ClientConnectionSettings] = None)(implicit actorSystem: ActorSystem) =
    new EtcdClientImpl(host, port, httpClientSettings)(actorSystem)

}