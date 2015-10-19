package pl.caltha.akka.cluster

import scala.concurrent.duration.DurationInt

import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.FlatSpecLike
import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar

import akka.actor.ActorSystem
import akka.actor.FSM.CurrentState
import akka.actor.FSM.Transition
import akka.testkit.TestKit
import akka.testkit.TestProbe

import pl.caltha.akka.etcd.EtcdClient

abstract class EtcdFSMSpecBase[State, Data](_system: ActorSystem)
    extends TestKit(_system) with FlatSpecLike with BeforeAndAfterAll with Matchers with MockitoSugar {

  import Mockito.{ when, verify }

  def this() =
    this(ActorSystem("testsystem"))

  def settings = ClusterDiscoverySettings.load(system.settings.config)

  def transitionTimeout = 10.seconds

  trait FixtureBase {

    val etcd = mock[EtcdClient]

    val stateProbe = TestProbe()

    def expectTransitionTo(expState: State) =
      stateProbe.expectMsgType[Transition[State]](transitionTimeout) should matchPattern {
        case Transition(_, _, state) if state == expState ⇒
      }

    def expectInitialState(expState: State) =
      stateProbe.expectMsgType[CurrentState[State]](transitionTimeout) should matchPattern {
        case CurrentState(_, state) if state == expState ⇒
      }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
