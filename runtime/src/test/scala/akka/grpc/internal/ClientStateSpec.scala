/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.duration._
import scala.concurrent.Promise

import io.grpc.ConnectivityState._

import akka.Done
import akka.actor.ActorSystem

import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ClientStateSpec extends AnyWordSpec with Matchers with ScalaFutures with Eventually with BeforeAndAfterAll {
  implicit val sys = ActorSystem()
  implicit val ec = sys.dispatcher
  implicit val patience = PatienceConfig(timeout = 10.seconds, interval = 150.milliseconds)

  private def clientState(channelCompletion: Promise[Done] = Promise[Done]()) = {
    val channel =
      new InternalChannel(new ChannelUtilsSpec.FakeChannel(Stream(IDLE, CONNECTING, READY)), channelCompletion.future)
    new ClientState(channel)
  }

  "Client State" should {
    "successfully provide a channel" in {
      // given a state
      val state = clientState()
      // it provides a channel when needed
      state.internalChannel should not be null
    }
    "reuse a valid channel" in {
      // given a state
      val state = clientState()
      // it provides a channel when needed
      val c1 = state.internalChannel.managedChannel
      val c2 = state.internalChannel.managedChannel
      c1 should be(c2)
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    sys.terminate()
  }
}
