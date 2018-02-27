package com.lightbend.grpc.interop

import akka.http.grpc.Grpc
import io.grpc.testing.integration.test
import io.grpc.testing.integration.test.{AkkaGrpcClientTester, TestServiceServiceHandler}
import org.scalatest._

class GrpcInteropSpec extends WordSpec with GrpcInteropTests {

  override val pendingAkkaServerTestCases = Seq(
    "custom_metadata",
    "status_code_and_message",
    "client_compressed_unary",
    "client_compressed_streaming")

  override val pendingAkkaClientTestCases: Seq[String] =
    Seq(
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

  javaGrpcTests()

  akkaHttpGrpcTests(implicit mat => implicit ec => TestServiceServiceHandler(new TestServiceImpl()))

  akkaHttpGrpcTestsWithAkkaGrpcClient(settings => new AkkaGrpcClientTester(settings))(implicit mat => implicit ec => TestServiceServiceHandler(new TestServiceImpl()))
}
