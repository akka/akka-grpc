package com.lightbend.grpc.interop

import io.grpc.StatusRuntimeException
import io.grpc.testing.integration.Util
import io.grpc.testing.integration2._
import org.scalatest.{Assertion, Succeeded, WordSpec}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

trait GrpcInteropTests { self: WordSpec =>
  import org.scalatest.Matchers._

  // see https://github.com/grpc/grpc/blob/master/tools/run_tests/run_interop_tests.py#L543
  val testCases = Seq(
    "large_unary",
    "empty_unary",
    "ping_pong",
    "empty_stream",
    "client_streaming",
    "server_streaming",
    "cancel_after_begin",
    "cancel_after_first_response",
    "timeout_on_sleeping_server",
    "custom_metadata",
    "status_code_and_message",
    "unimplemented_method",
    "client_compressed_unary",
    "client_compressed_streaming",
    "server_compressed_unary",
    "server_compressed_streaming",
    "unimplemented_service",
  )

  def grpcTests(serverHandlerProvider: ServerHandlerProvider, clientTesterProvider: ClientTesterProvider) =
    serverHandlerProvider.label + " with " + clientTesterProvider.label should {
      testCases.foreach { testCaseName =>
        s"pass the $testCaseName integration test" in {
          val allPending = serverHandlerProvider.pendingCases ++ clientTesterProvider.pendingCases
          pendingTestCaseSupport(allPending(testCaseName)) {
            withGrpcServer(serverHandlerProvider.server) {
              runGrpcClient(testCaseName)(clientTesterProvider.clientTesterFactory)
            }
          }
        }
      }
    }


  private def runGrpcClient(testCaseName: String)(clientTesterFactory: Settings => ExecutionContext => ClientTester): Unit = {
    val args: Array[String] = Array("--server_host_override=foo.test.google.fr", "--use_test_ca=true", s"--test_case=$testCaseName")

    Util.installConscryptIfAvailable()
    val settings = Settings.parseArgs(args)
    val client = new TestServiceClient(clientTesterFactory(settings)(ExecutionContext.global))
    client.setUp()

    try
      client.run(settings)
    finally
      client.tearDown()
  }


  private def pendingTestCaseSupport(expectedToFail: Boolean)(block: => Unit): Assertion = {
    val result = try {
      block
      Succeeded
    } catch {
      case NonFatal(_) if expectedToFail => pending
    }

    result match {
      case Succeeded if expectedToFail => fail("Succeeded against expectations")
      case res => res
    }
  }

  private def withGrpcServer[T](server: GrpcServer[T])(block: => Unit): Assertion = {
    try {
      val binding = server.start()
      try {
        block
      } finally {
        server.stop(binding)
      }
      Succeeded
    } catch {
      case e: StatusRuntimeException =>
        // 'Status' is not serializable, so we have to unpack the exception
        // to avoid trouble when running tests from sbt
        if (e.getCause == null) fail(e.getMessage)
        else fail(e.getMessage, e.getCause)
      case NonFatal(t) => fail(t)
    }
  }
}


trait PendingCases {
  def label: String
  def pendingCases: Set[String]
}

trait ServerHandlerProvider extends PendingCases {
  def server: GrpcServer[_]
}

trait  ClientTesterProvider extends PendingCases {
  def clientTesterFactory: Settings => ExecutionContext => ClientTester
}

trait GrpcJavaPendingCases extends PendingCases {

  val pendingCases =
    Set(
      "client_compressed_unary",
      "client_compressed_streaming"
    )
}

object IoGrpcJavaServer extends GrpcJavaPendingCases with ServerHandlerProvider {
  val label: String = "grcp-java server"
  val server = IoGrpcServer
}

object GrpcJavaClientTesterProvider extends ClientTesterProvider with GrpcJavaPendingCases {

  val label: String = "grpc-java client tester"
  val clientTesterFactory: Settings => ExecutionContext => ClientTester = settings => ExecutionContext => new GrpcJavaClientTester(settings)
}

trait AkkaHttpServerProvider extends ServerHandlerProvider {

  val label: String = "akka-grpc server scala"
  val pendingCases =
    Set(
      "custom_metadata",
      "status_code_and_message",
      "client_compressed_unary",
      "client_compressed_streaming"
    )
}

trait AkkaClientTestProvider extends ClientTesterProvider {

  val label: String = "akka-grpc client tester"

  val pendingCases =
    Set(
      "ping_pong",
      "empty_stream",
      "client_streaming",
      "server_streaming",
      "cancel_after_begin",
      "cancel_after_first_response",
      "timeout_on_sleeping_server",
      "custom_metadata",
      "status_code_and_message",
      "client_compressed_unary",
      "client_compressed_streaming",
      "server_compressed_unary",
      "server_compressed_streaming",
      "unimplemented_service",
    )
}
