/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

import org.junit.AssumptionViolatedException
import org.scalatest.Args
import org.scalatest.Reporter
import org.scalatest.events.{ Event, TestCanceled, TestFailed, TestPending, TestSucceeded }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer

/**
 * Tests the interop test harness itself, without starting any real servers or clients.
 */
class GrpcInteropTestsHarnessSpec extends AnyWordSpec with Matchers {

  object NoopServerProvider extends GrpcServerProvider {
    val label = "noop server"
    val pendingCases: Set[String] = Set.empty
    val server: GrpcServer[Unit] = new GrpcServer[Unit] {
      def start(args: Array[String]): Unit = ()
      def getPort(binding: Unit): Int = 0
      def stop(binding: Unit): Unit = ()
    }
  }

  class ThrowingClientProvider(exception: => Throwable) extends GrpcClientProvider {
    val label = "throwing client"
    val pendingCases: Set[String] = Set.empty
    val client: GrpcClient = new GrpcClient {
      def run(args: Array[String]): Unit = throw exception
    }
  }

  private def runInterop(clientProvider: GrpcClientProvider): (Int, Seq[Event]) = {
    val events = ListBuffer.empty[Event]
    val reporter = new Reporter {
      def apply(event: Event): Unit = events.synchronized { events += event }
    }
    val interopTests = new GrpcInteropTests(NoopServerProvider, clientProvider)
    interopTests.run(None, Args(reporter))
    (interopTests.testCases.size, events.toList)
  }

  "The interop test harness" should {

    "cancel rather than fail a test case when the grpc-java tester gives up because of a violated assumption" in {
      // grpc-java's AbstractInteropTest.assumeEnoughMemory() throws this when the JVM has too little free heap
      val (testCaseCount, events) =
        runInterop(new ThrowingClientProvider(new AssumptionViolatedException("42 is not sufficient to run this test")))

      events.collect { case f: TestFailed => f.testName } shouldBe empty
      events.collect { case c: TestCanceled => c }.size shouldBe testCaseCount
    }

    "still fail a test case on other exceptions" in {
      val (testCaseCount, events) = runInterop(new ThrowingClientProvider(new RuntimeException("boom")))

      events.collect { case c: TestCanceled => c } shouldBe empty
      events.collect { case p: TestPending => p } shouldBe empty
      events.collect { case s: TestSucceeded => s } shouldBe empty
      events.collect { case f: TestFailed => f }.size shouldBe testCaseCount
    }
  }
}
