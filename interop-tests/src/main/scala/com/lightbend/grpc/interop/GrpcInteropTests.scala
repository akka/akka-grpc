package com.lightbend.grpc.interop

import io.grpc.StatusRuntimeException
import org.scalatest.{Assertion, Succeeded, WordSpec}

import scala.util.control.NonFatal


trait GrpcInteropTests {
  self: WordSpec =>

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

  def grpcTests(serverProvider: GrpcServerProvider, clientProvider: GrpcClientProvider) = {

    val server = serverProvider.server
    val client = clientProvider.client

    server.label + " with " + client.label should {
      testCases.foreach { testCaseName =>
        s"pass the $testCaseName integration test" in {
          val allPending = server.pendingCases ++ client.pendingCases
          pendingTestCaseSupport(allPending(testCaseName)) {
            withGrpcServer(server) {
              runGrpcClient(testCaseName)(client)
            }
          }
        }
      }
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

  private def runGrpcClient(testCaseName: String)(client: GrpcClient): Unit = {
    val args: Array[String] = Array("--server_host_override=foo.test.google.fr", "--use_test_ca=true", s"--test_case=$testCaseName")
    client.run(args)
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


}


trait GrpcServerProvider {
  def server: GrpcServer[_]
}

trait GrpcClientProvider {
  def client: GrpcClient
}

object IoGrpcJavaServerProvider extends GrpcServerProvider {
  val server = IoGrpcServer
}

object IoGrpcJavaClientProvider extends GrpcClientProvider {
  val client = IoGrpcClient
}

trait AkkaHttpServerProvider extends GrpcServerProvider

trait AkkaHttpClientProvider extends GrpcClientProvider
