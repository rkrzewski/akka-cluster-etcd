package akka.cluster

import akka.actor.AddressFromURIString

/**
  * We need to construct instances of `Member` for test purposes, but `Member.apply` is private.
  * This little helper class lets us to pry the lid open.
  */
object MemberFactory {
  def apply(addressUriString: String, roles: Set[String], status: MemberStatus) =
    Member(UniqueAddress(AddressFromURIString(addressUriString), 0), roles).copy(status)
}
