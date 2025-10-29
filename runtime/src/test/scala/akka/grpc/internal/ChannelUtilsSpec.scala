/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.TimeUnit
import akka.Done
import akka.event.{ LoggingAdapter, NoLogging }
import akka.grpc.internal.ChannelUtilsSpec.FakeChannel
import io.grpc.ConnectivityState._
import io.grpc._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.nowarn
import scala.concurrent.Promise
import scala.util.Failure

object ChannelUtilsSpec {
  @nowarn("msg=deprecated") // Stream -> LazyList
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
    val log: LoggingAdapter = NoLogging

    "should fail if enter into failure configured number of times" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
      val fakeChannel = new FakeChannel(
        Stream(IDLE, CONNECTING, TRANSIENT_FAILURE, CONNECTING, TRANSIENT_FAILURE, CONNECTING, TRANSIENT_FAILURE))

      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), log)
      // IDLE => CONNECTING
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual false
      promiseDone.isCompleted shouldEqual false
      // CONNECTING => FAILURE
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual false
      promiseDone.isCompleted shouldEqual false
      // FAILURE => CONNECTING
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual false
      promiseDone.isCompleted shouldEqual false
      // CONNECTING => FAILURE
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual true
      promiseReady.future.value.get shouldBe a[Failure[_]]
      promiseReady.future.failed.value.get.get.getMessage should startWith("Unable to establish connection")
    }

    "should reset counter if enters into ready" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
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
      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), log)
      // IDLE => CONNECTING
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual false
      promiseDone.isCompleted shouldEqual false
      // CONNECTING => FAILURE
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual false
      promiseDone.isCompleted shouldEqual false
      // FAILURE => CONNECTING
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual false
      promiseDone.isCompleted shouldEqual false
      // CONNECTING => READY
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual true
      // going into ready should have reset counter
      promiseDone.isCompleted shouldEqual false

      // READY => FAILURE
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual true
      promiseDone.isCompleted shouldEqual false
      // FAILURE => CONNECTING
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual true
      promiseDone.isCompleted shouldEqual false
      // CONNECTING => FAILURE
      fakeChannel.runCallBack()
      // 2 in a row now with max failures = 1
      promiseReady.isCompleted shouldEqual true
      promiseDone.isCompleted shouldEqual true

      promiseDone.future.value.get shouldBe a[Failure[_]]
      promiseDone.future.failed.value.get.get.getMessage should startWith("Unable to establish connection")
    }

    "should stop monitoring if SHUTDOWN" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
      val fakeChannel = new FakeChannel(Stream(IDLE, CONNECTING, READY) ++ Stream.continually(SHUTDOWN))
      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), log)
      // IDLE => CONNECTING
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual false
      promiseDone.isCompleted shouldEqual false
      // CONNECTING => READY
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual true
      promiseDone.isCompleted shouldEqual false
      // READY => SHUTDOWN as its checked after the call back
      fakeChannel.runCallBack()
      promiseReady.isCompleted shouldEqual true
      promiseDone.isCompleted shouldEqual true
      promiseDone.future.futureValue shouldEqual Done
    }
  }
}
