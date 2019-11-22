/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.TimeoutException

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, Promise }

import io.grpc.ConnectivityState._
import io.grpc.ManagedChannel

import akka.Done
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer

import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ AsyncWordSpec, BeforeAndAfterAll, Matchers }

class ClientStateSpec extends AsyncWordSpec with Matchers with ScalaFutures with Eventually with BeforeAndAfterAll {
  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = sys.dispatcher
  implicit val patience = PatienceConfig(timeout = 5.seconds, interval = 150.milliseconds)

  private val mockSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt("somehost.com", 3434)

  private def clientState(channelCompletion: Promise[Done] = Promise[Done]()) = {
    val channelFactory: GrpcClientSettings => Future[InternalChannel] = { _ =>
      Future.successful(
        new InternalChannel(
          new ChannelUtilsSpec.FakeChannel(Stream(IDLE, CONNECTING, READY)),
          channelCompletion.future))
    }
    new ClientState(mockSettings, sys.log, channelFactory)
  }

  def userCodeToLiftChannel: Future[ManagedChannel] => ManagedChannel = { eventualChannel =>
    Await.result(eventualChannel, 1.second)
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
    "successfully provide a channel after initial creation failure" in {
      var channel: Future[ManagedChannel] = Future.failed(new IllegalStateException("No targets returned for name"))
      val channelCompletion = Promise[Done]()
      val channelFactory: GrpcClientSettings => Future[InternalChannel] = { _ =>
        channel.map(InternalChannel(_, channelCompletion.future))
      }

      val state = new ClientState(mockSettings, sys.log, channelFactory)

      // Initially, looking up the channel times out since the creating is still retrying
      assertThrows[TimeoutException](state.withChannel(userCodeToLiftChannel))

      // Then when it starts succeeding...
      channel = Future.successful(new ChannelUtilsSpec.FakeChannel(Stream(IDLE, CONNECTING, READY)))

      eventually {
        // eventually the state produces a new channel
        val channel = state.withChannel(userCodeToLiftChannel)
        channel should not be null
      }

      // Then when we close it
      state.close()

      // we can no longer make new calls
      assertThrows[ClientClosedException](state.withChannel(userCodeToLiftChannel))

      // but it is not closed
      assertThrows[Exception](state.closed().futureValue)

      // until the underlying channel is completed
      channelCompletion.success(Done)
      state.closed().futureValue should be(Done)
    }

    "successfully recreate a channel when it fails with a ClientConnectionException after initially being created successfully" in {
      val firstChannel = InternalChannel(
        new ChannelUtilsSpec.FakeChannel(Stream(IDLE, CONNECTING, READY)),
        Future.failed(new ClientConnectionException("Test")))
      val secondChannel =
        InternalChannel(new ChannelUtilsSpec.FakeChannel(Stream(IDLE, CONNECTING, READY)), Promise().future)

      var firstSent = false

      val channelFactory: GrpcClientSettings => Future[InternalChannel] = { _ =>
        if (firstSent)
          Future.successful(secondChannel)
        else {
          firstSent = true
          Future.successful(firstChannel)
        }
      }

      val state = new ClientState(mockSettings, sys.log, channelFactory)

      // Initially, looking up the channel times out since the creating is still retrying
      eventually {
        state.withChannel(userCodeToLiftChannel) should equal(secondChannel.managedChannel)
      }
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    sys.terminate()
  }
}
