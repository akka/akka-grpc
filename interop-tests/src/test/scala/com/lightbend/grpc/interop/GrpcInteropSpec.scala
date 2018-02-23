package com.lightbend.grpc.interop

import akka.http.grpc.Grpc
import io.grpc.testing.integration.test.TestServiceServiceHandler
import org.scalatest._

class GrpcInteropSpec extends WordSpec with GrpcInteropTests {
  override val pendingAkkaTestCases = Seq(
    "ping_pong",
    "server_streaming",
    "cancel_after_first_response",
    "custom_metadata",
    "status_code_and_message",
    "unimplemented_method",
    "client_compressed_unary",
    "client_compressed_streaming",
    "server_compressed_streaming",
  )

  javaGrpcTests()
  akkaHttpGrpcTests(implicit mat => implicit ec => TestServiceServiceHandler(new TestServiceImpl()))
}
