package pl.caltha.akka.etcd

import java.time.ZonedDateTime

/**
  * Represents a message returned by `etcd`, either [[EtcdResponse]] or [[EtcdError]].
  */
sealed trait EtcdMessage

/**
  * A response returned after a successful operation.
  *
  * @param action action that was performed one of: `get`, `set`, `create`, `delete`, `compareAndSwap`, `compareAndDelete`.
  * @param node the node on which operation was performed
  * @param prevNode previous state of the node before the operation, returned for `set` and `compareAndSwap` operations.
  */
case class EtcdResponse(action: String, node: EtcdNode, prevNode: Option[EtcdNode]) extends EtcdMessage

/**
  * A response returned after a failed operation.
  *
  * @param errorCode numerical error code.
  * @param message textual error message.
  * @param cause additional information about the error.
  * @param index current write index of the node on which operation was requested.
  */
case class EtcdError(errorCode: Int, message: String, cause: String, index: Int) extends EtcdMessage

/**
  * Various `etcd` error codes are provided as constants.
  *
  * See also [[https://coreos.com/etcd/docs/latest/errorcode.html Etcd documentation]]
  */
object EtcdError {

  /** Error codes related to issued command. */
  val CommandError = Range(100, 199)

  /** Error codes related to invalid command payload. */
  val POSTFormError = Range(200, 299)

  /** Error codes related to `etcd` internal problems. */
  val InternalError = Range(300, Int.MaxValue)

  /** Key not found.*/
  val KeyNotFound = 100

  /** Compare failed. */
  val TestFailed = 101

  /** Not a file. */
  val NotFile = 102

  /** Not a directory. */
  val NotDir = 104

  /** Key already exists. */
  val NodeExist = 105

  /** Root is read only. */
  val RootROnly = 107

  /** Directory not empty. */
  val DirNotEmpty = 108

  /** PrevValue is Required in POST form. */
  val PrevValueRequired = 201

  /** The given TTL in POST form is not a number. */
  val TTLNaN = 202

  /** The given index in POST form is not a number. */
  val IndexNaN = 203

  /** Invalid field. */
  val InvalidField = 209

  /** Invalid POST form. */
  val InvalidForm = 210

  /** Raft Internal Error */
  val RaftInternal = 300

  /** During Leader Election */
  val LeaderElect = 301

  /** Watcher is cleared due to etcd recovery. */
  val WatcherCleared = 400

  /** The event in requested index is outdated and cleared. */
  val EventIndexCleared = 401
}

/**
  * Represents a node in `etcd` key space.
  *
  * @param key the node's key. In case of nested nodes, is prepended with a path composed of parent directory
  *            nodes' keys, separated with `/` characters
  * @param createdIndex journal index at which the node was created.
  * @param modifiedIndex journal index at which the node was most recently modified.
  * @param expiration node expiration time.
  * @param value the value stored in the node.
  * @param dir a flag indicating if the node is a directory or leaf ("file") node.
  * @param nodes directory's immediate child nodes, returned on a recursive `get` operation.
  */
case class EtcdNode(key: String, createdIndex: Int, modifiedIndex: Int, expiration: Option[ZonedDateTime],
                    value: Option[String], dir: Option[Boolean], nodes: Option[List[EtcdNode]])
