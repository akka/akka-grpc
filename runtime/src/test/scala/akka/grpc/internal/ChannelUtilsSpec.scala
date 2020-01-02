/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.TimeUnit

import akka.Done
import akka.grpc.internal.ChannelUtilsSpec.FakeChannel
import io.grpc._
import ConnectivityState._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Promise
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object ChannelUtilsSpec {
  class FakeChannel(stateResponses: Stream[ConnectivityState]) extends ManagedChannel {
    var closed = false
    var nextResponse = stateResponses
    var currentCallBack: Runnable = null
    override def shutdown(): ManagedChannel = {
      closed = true
      this
    }
    override def isShutdown: Boolean = ???
    override def isTerminated: Boolean = ???
    override def shutdownNow(): ManagedChannel = ???
    override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = ???
    override def newCall[RequestT, ResponseT](
        methodDescriptor: MethodDescriptor[RequestT, ResponseT],
        callOptions: CallOptions): ClientCall[RequestT, ResponseT] = ???
    override def authority(): String = ???

    override def getState(requestConnection: Boolean): ConnectivityState = {
      val next = nextResponse.head
      nextResponse = nextResponse.tail
      next
    }

    override def notifyWhenStateChanged(source: ConnectivityState, callback: Runnable): Unit =
      currentCallBack = callback

    def runCallBack(): Unit = {
      val callb = currentCallBack
      currentCallBack = null
      callb.run()
    }
  }
}

class ChannelUtilsSpec extends AnyWordSpec with Matchers with ScalaFutures {
  "Channel monitor" should {
    "should fail if enter into failure configured number of times" in {
      val promise = Promise[Done]
      val fakeChannel = new FakeChannel(
        Stream(IDLE, CONNECTING, TRANSIENT_FAILURE, CONNECTING, TRANSIENT_FAILURE, CONNECTING, TRANSIENT_FAILURE))

      ChannelUtils.monitorChannel(promise, fakeChannel, Some(2))
      // IDLE => CONNECTING
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual false
      // CONNECTING => FAILURE
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual false
      // FAILURE => CONNECTING
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual false
      // CONNECTING => FAILURE
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual true
      promise.future.failed.futureValue.getMessage should startWith("Unable to establish connection")
      fakeChannel.closed shouldEqual true
    }

    "should reset counter if enters into ready" in {
      val promise = Promise[Done]
      val fakeChannel =
        new FakeChannel(
          Stream(
            IDLE,
            CONNECTING,
            TRANSIENT_FAILURE,
            CONNECTING,
            READY,
            TRANSIENT_FAILURE,
            CONNECTING,
            TRANSIENT_FAILURE))
      ChannelUtils.monitorChannel(promise, fakeChannel, Some(2))
      // IDLE => CONNECTING
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual false
      // CONNECTING => FAILURE
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual false
      // FAILURE => CONNECTING
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual false
      // CONNECTING => READY
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual false
      // READY => FAILURE
      fakeChannel.runCallBack()
      // going into ready should have reset counter
      promise.isCompleted shouldEqual false

      // FAILURE => CONNECTING
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual false
      // CONNECTING => FAILURE
      fakeChannel.runCallBack()
      // 2 in a row now with max failures = 1
      promise.isCompleted shouldEqual true

      val failure = promise.future.failed.futureValue
      failure shouldBe an[ClientConnectionException]
      failure.getMessage should startWith("Unable to establish connection")
    }

    "should stop monitoring if SHUTDOWN" in {
      val promise = Promise[Done]
      val fakeChannel = new FakeChannel(Stream(IDLE, CONNECTING, READY) ++ Stream.continually(SHUTDOWN))
      ChannelUtils.monitorChannel(promise, fakeChannel, Some(2))
      // IDLE => CONNECTING
      fakeChannel.runCallBack()
      promise.isCompleted shouldEqual false
      // CONNECTING => READY
      fakeChannel.runCallBack()
      // READY => SHUTDOWN as its checked after the call back
      promise.isCompleted shouldEqual true
      promise.future.futureValue shouldEqual Done
    }
  }
}
