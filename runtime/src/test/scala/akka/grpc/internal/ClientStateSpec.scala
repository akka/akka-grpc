/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.Done
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import io.grpc.ConnectivityState._
import io.grpc.ManagedChannel
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ AsyncWordSpec, BeforeAndAfterAll, Matchers }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, Promise }

class ClientStateSpec extends AsyncWordSpec with Matchers with ScalaFutures with Eventually with BeforeAndAfterAll {

  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = sys.dispatcher
  implicit val patience = PatienceConfig(timeout = 5.seconds, interval = 150.milliseconds)

  private val mockSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt("somehost.com", 3434)

  private def clientState(channelCompletion: Promise[Done] = Promise[Done]()) = {
    val channelFactory: GrpcClientSettings => InternalChannel = { _ =>
      val channel: Future[ManagedChannel] =
        Future.successful(new ChannelUtilsSpec.FakeChannel(Stream(IDLE, CONNECTING, READY)))
      new InternalChannel(channel, channelCompletion)
    }
    new ClientState(mockSettings, channelFactory)
  }

  def userCodeToLiftChannel: Future[ManagedChannel] => ManagedChannel = { eventualChannel =>
    Await.result(eventualChannel, 5.seconds)
  }

  "Client State" should {
    "successfully provide a channel" in {
      // given a state
      val state = clientState()
      // it provides a channel when needed
      val channel = state.withChannel(userCodeToLiftChannel)
      channel should not be null
    }
    "reuse a valid channel" in {
      // given a state
      val state = clientState()
      // it provides a channel when needed
      val c1 = state.withChannel(userCodeToLiftChannel)
      val c2 = state.withChannel(userCodeToLiftChannel)
      c1 should be(c2)
    }
    "fail to provide a channel when the client state has been closed" in {
      // given a state
      val state = clientState()
      // it provides a channel when needed
      val channel = state.withChannel(userCodeToLiftChannel)
      channel should not be null
      state.close()
      assertThrows[ClientClosedException] {
        state.withChannel(userCodeToLiftChannel)
      }
    }
    "successfully provide a channel after a failure" in {
      // given a state we can complete from the outside
      val channelCompletion = Promise[Done]()
      val state = clientState(channelCompletion)
      // it provides a channel when needed
      val c1 = state.withChannel(userCodeToLiftChannel)
      // and, if the channel is failed
      channelCompletion.tryFailure(new ClientConnectionException(s"Unable to establish connection"))

      eventually {
        // eventually the state produces a new channel
        val channel = state.withChannel(userCodeToLiftChannel)
        channel should not be null
        channel should not be c1
      }
    }

    "successfully provide a channel after a lookup failure" in {
      var channel: Future[ManagedChannel] = Future.failed(new IllegalStateException("No targets returned for name"))
      var channelCompletion = Promise[Done]()
      val channelFactory: GrpcClientSettings => InternalChannel = { _ =>
        new InternalChannel(channel, channelCompletion)
      }

      val state = new ClientState(mockSettings, channelFactory)

      assertThrows[IllegalStateException](state.withChannel(userCodeToLiftChannel))

      channelCompletion.tryFailure(new NoTargetException("No targets returned for name"))

      channel = Future.successful(new ChannelUtilsSpec.FakeChannel(Stream(IDLE, CONNECTING, READY)))
      channelCompletion = Promise[Done]()

      eventually {
        // eventually the state produces a new channel
        val channel = state.withChannel(userCodeToLiftChannel)
        channel should not be null
      }
    }
  }
}
