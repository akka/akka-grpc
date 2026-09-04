/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.TimeUnit
import akka.Done
import akka.event.{ LoggingAdapter, NoLogging }
import akka.grpc.internal.ChannelUtilsSpec.{ FakeChannel, ManualScheduler }
import io.grpc.ConnectivityState._
import io.grpc._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.nowarn
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Failure

object ChannelUtilsSpec {
  @nowarn("msg=deprecated") // Stream -> LazyList
  class FakeChannel(stateResponses: Stream[ConnectivityState]) extends ManagedChannel {
    var closed = false
    var nextResponse = stateResponses
    var currentCallBack: Runnable = null
    var enterIdleCalls = 0
    var requestConnectionCalls = 0
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

    override def enterIdle(): Unit = enterIdleCalls += 1

    override def getState(requestConnection: Boolean): ConnectivityState = {
      if (requestConnection) requestConnectionCalls += 1
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

  /** Captures scheduled watchdog tasks so tests can trigger them manually instead of waiting on a real clock. */
  class ManualScheduler {
    private var scheduled: List[() => Unit] = Nil
    val schedule: (FiniteDuration, () => Unit) => Unit = (_, task) => scheduled ::= task
    def scheduledCount: Int = scheduled.size
    def runScheduled(): Unit = {
      val toRun = scheduled.reverse
      scheduled = Nil
      toRun.foreach(_())
    }
  }
}

class ChannelUtilsSpec extends AnyWordSpec with Matchers with ScalaFutures {
  "Channel monitor" should {
    val log: LoggingAdapter = NoLogging
    val noopSchedule: (FiniteDuration, () => Unit) => Unit = (_, _) => ()

    "should fail if enter into failure configured number of times" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
      val fakeChannel = new FakeChannel(
        Stream(IDLE, CONNECTING, TRANSIENT_FAILURE, CONNECTING, TRANSIENT_FAILURE, CONNECTING, TRANSIENT_FAILURE))

      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), Duration.Inf, log)(noopSchedule)
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
      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), Duration.Inf, log)(noopSchedule)
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
      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), Duration.Inf, log)(noopSchedule)
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

    "should not schedule a connecting watchdog when connecting-timeout is infinite" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
      val fakeChannel = new FakeChannel(Stream.continually(CONNECTING))
      var scheduleCalls = 0
      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), Duration.Inf, log)((_, _) =>
        scheduleCalls += 1)
      scheduleCalls shouldEqual 0
    }

    "should abandon a connection stuck CONNECTING once connecting-timeout elapses" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
      // Never leaves CONNECTING on its own - only the watchdog firing can move things along.
      val fakeChannel = new FakeChannel(Stream.continually(CONNECTING))
      val scheduler = new ManualScheduler

      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), 20.seconds, log)(scheduler.schedule)
      scheduler.scheduledCount shouldEqual 1
      fakeChannel.enterIdleCalls shouldEqual 0

      // connecting-timeout elapses while still CONNECTING
      scheduler.runScheduled()
      fakeChannel.enterIdleCalls shouldEqual 1
      fakeChannel.requestConnectionCalls should be >= 1
    }

    "should leave a connection alone if it left CONNECTING before connecting-timeout elapses" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
      val fakeChannel = new FakeChannel(Stream(CONNECTING, READY, READY))
      val scheduler = new ManualScheduler

      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), 20.seconds, log)(scheduler.schedule)

      // by the time the watchdog fires, the channel already became READY
      scheduler.runScheduled()
      fakeChannel.enterIdleCalls shouldEqual 0
    }
  }
}
