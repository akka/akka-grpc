/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
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
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ClientStateSpec extends AnyWordSpec with Matchers with ScalaFutures with Eventually with BeforeAndAfterAll {
  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = sys.dispatcher
  implicit val patience = PatienceConfig(timeout = 10.seconds, interval = 150.milliseconds)

  private val mockSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt("somehost.com", 3434)

  private def clientState(channelCompletion: Promise[Done] = Promise[Done]()) = {
    val channel =
      new InternalChannel(new ChannelUtilsSpec.FakeChannel(Stream(IDLE, CONNECTING, READY)), channelCompletion.future)
    new ClientState(mockSettings, sys.log, channel)
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
  }

  override def afterAll(): Unit = {
    super.afterAll()
    sys.terminate()
  }
}
