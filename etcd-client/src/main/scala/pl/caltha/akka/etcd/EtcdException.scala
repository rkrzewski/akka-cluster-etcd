package pl.caltha.akka.etcd

/**
  * Thrown when `etcd` request fails.
  *
  * @param error information about the error provided by `etcd`.
  */
abstract class EtcdException(val error: EtcdError) extends RuntimeException

/**
  * Indicates an operation that cannot be completed because provided parameters don't match the current
  * contents of the key space.
  */
class EtcdCommandException(error: EtcdError) extends EtcdException(error)

/**
  * Indicates that the format of the message sent by the client was incorrect.
  *
  * Shouldn't happen, unless there is a bug in Scala client library, of course.
  */
class EtcdPostFormException(error: EtcdError) extends EtcdException(error)

/**
  * Indicates that `etcd` server was unable to complete the operation because of an internal problem.
  */
class EtcdInternalException(error: EtcdError) extends EtcdException(error)

/**
  * Factory of [[EtcdException]] objects.
  */
object EtcdException {

  /**
    * Creates an instance of a subclass of `EtcdException`, approprate for the error code provided
    * in `error` parameter.
    *
    * @param error information about the error provided by `etcd`.
    */
  def apply(error: EtcdError) = {
    if (EtcdError.CommandError.contains(error.errorCode)) {
      new EtcdCommandException(error)
    } else if (EtcdError.POSTFormError.contains(error.errorCode)) {
      new EtcdPostFormException(error)
    } else {
      new EtcdInternalException(error)
    }
  }

  /**
    * Extracts [[EtcdError]] value from a [[EtcdException]] instance.
    *
    * This allows pattern matching on EtcdExceptions:
    * {{{
    * ex match {
    *   case EtcdException(EtcdError(code, _, _, _)) if EtcdError.ClientErrors.contains(code) => ...
    * }
    * }}}
    */
  def unapply(ex: EtcdException): Option[EtcdError] =
    Some(ex.error)
}