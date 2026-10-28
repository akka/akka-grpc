/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.TimeUnit
import akka.Done
import akka.actor.Cancellable
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
    var enterIdleThrows: Option[Throwable] = None
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

    override def enterIdle(): Unit = {
      enterIdleCalls += 1
      enterIdleThrows.foreach(throw _)
    }

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
    private class FakeCancellable extends Cancellable {
      @volatile private var cancelled = false
      override def cancel(): Boolean = {
        val wasPending = !cancelled
        cancelled = true
        wasPending
      }
      override def isCancelled: Boolean = cancelled
    }
    private case class Scheduled(task: () => Unit, cancellable: FakeCancellable)
    private var scheduled: List[Scheduled] = Nil
    val schedule: (FiniteDuration, () => Unit) => Cancellable = (_, task) => {
      val cancellable = new FakeCancellable
      scheduled ::= Scheduled(task, cancellable)
      cancellable
    }
    def scheduledCount: Int = scheduled.size
    def runScheduled(): Unit = {
      val toRun = scheduled.reverse
      scheduled = Nil
      toRun.foreach(s => if (!s.cancellable.isCancelled) s.task())
    }
  }
}

class ChannelUtilsSpec extends AnyWordSpec with Matchers with ScalaFutures {
  "Channel monitor" should {
    val log: LoggingAdapter = NoLogging
    val neverCancelled: Cancellable = new Cancellable {
      override def cancel(): Boolean = false
      override def isCancelled: Boolean = false
    }
    val noopSchedule: (FiniteDuration, () => Unit) => Cancellable = (_, _) => neverCancelled

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
      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), Duration.Inf, log)((_, _) => {
        scheduleCalls += 1
        neverCancelled
      })
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

    "should cancel a stale connecting watchdog so it cannot abandon a later, unrelated CONNECTING episode" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
      // Episode 1: CONNECTING -> READY -> IDLE. Episode 2 then starts a brand new, legitimate
      // CONNECTING that is still ongoing when episode 1's (long-expired) watchdog would fire.
      val fakeChannel = new FakeChannel(Stream(CONNECTING, READY, IDLE, CONNECTING) ++ Stream.continually(CONNECTING))
      val scheduler = new ManualScheduler

      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), 20.seconds, log)(scheduler.schedule)
      // CONNECTING (episode 1) => READY: must cancel episode 1's watchdog
      fakeChannel.runCallBack()
      // READY => IDLE
      fakeChannel.runCallBack()
      // IDLE => CONNECTING (episode 2): schedules a fresh watchdog
      fakeChannel.runCallBack()

      fakeChannel.enterIdleCalls shouldEqual 0
      // If episode 1's stale watchdog weren't cancelled, it would ALSO fire here (mistaking
      // episode 2's fresh CONNECTING for its own) and abandon episode 2 prematurely, on top of
      // episode 2's own (legitimate) watchdog - i.e. enterIdleCalls would incorrectly be 2.
      scheduler.runScheduled()
      fakeChannel.enterIdleCalls shouldEqual 1
    }

    "should count a watchdog-abandoned attempt towards maxConnectionAttempts, same as a TRANSIENT_FAILURE" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
      // A peer that only ever hangs mid-handshake: never legitimately leaves CONNECTING on its
      // own, so only the watchdog can ever move things along.
      val fakeChannel = new FakeChannel(Stream.continually(CONNECTING))
      val scheduler = new ManualScheduler

      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), 20.seconds, log)(scheduler.schedule)

      // 1st watchdog cycle: attempt 1 of 2, not exhausted yet - abandons and retries.
      scheduler.runScheduled()
      fakeChannel.enterIdleCalls shouldEqual 1
      promiseReady.isCompleted shouldEqual false

      // 2nd watchdog cycle: attempt 2 of 2 - exhausted, so this must give up instead of retrying
      // again, exactly like two TRANSIENT_FAILUREs in a row would with maxConnectionAttempts = 2.
      scheduler.runScheduled()
      fakeChannel.enterIdleCalls shouldEqual 1 // no 3rd attempt - already gave up
      promiseReady.isCompleted shouldEqual true
      promiseReady.future.value.get shouldBe a[Failure[_]]
      promiseReady.future.failed.value.get.get.getMessage should startWith("Unable to establish connection")
    }

    "should fail the promises instead of hanging forever if abandoning a stuck connection throws" in {
      val promiseReady = Promise[Unit]()
      val promiseDone = Promise[Done]()
      val fakeChannel = new FakeChannel(Stream.continually(CONNECTING))
      // e.g. a concurrent close() of the channel while the watchdog is abandoning it.
      fakeChannel.enterIdleThrows = Some(new RuntimeException("boom"))
      val scheduler = new ManualScheduler

      ChannelUtils.monitorChannel(promiseReady, promiseDone, fakeChannel, Some(2), 20.seconds, log)(scheduler.schedule)
      promiseReady.isCompleted shouldEqual false

      // Without a try/catch around the abandon-and-retry block, `advanced` would already be
      // claimed by this point, silencing the "normal" notifyWhenStateChanged callback too - so
      // the promises would never complete and the client would hang forever with no signal.
      scheduler.runScheduled()
      promiseReady.isCompleted shouldEqual true
      promiseReady.future.value.get shouldBe a[Failure[_]]
      val failure = promiseReady.future.failed.value.get.get
      failure shouldBe a[ClientConnectionException]
      failure.getCause.getMessage shouldEqual "boom"
    }
  }
}
