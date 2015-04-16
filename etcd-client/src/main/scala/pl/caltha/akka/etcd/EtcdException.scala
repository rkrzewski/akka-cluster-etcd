package pl.caltha.akka.etcd

abstract class EtcdException(val error: EtcdError) extends RuntimeException

class EtcdCommandException(error: EtcdError) extends EtcdException(error)

class EtcdPostFormException(error: EtcdError) extends EtcdException(error)

class EtcdRaftException(error: EtcdError) extends EtcdException(error)

class EtcdInternalException(error: EtcdError) extends EtcdException(error)

object EtcdException {
  def apply(error: EtcdError) = {
    if (error.errorCode <= 200) {
      new EtcdCommandException(error)
    } else if (error.errorCode <= 300) {
      new EtcdPostFormException(error)
    } else if (error.errorCode <= 400) {
      new EtcdRaftException(error)
    } else {
      new EtcdInternalException(error)
    }
  }

  def unapply(ex: EtcdException): Option[EtcdError] =
    Some(ex.error)
}