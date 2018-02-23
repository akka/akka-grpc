package com.lightbend.grpc.interop

import akka.http.grpc.Grpc
import io.grpc.testing.integration.test.TestServiceServiceHandler
import org.scalatest._

class GrpcInteropSpec extends WordSpec with GrpcInteropTests {
  override val pendingAkkaTestCases = Seq(
    "custom_metadata",
    "status_code_and_message",
    "client_compressed_unary",
    "client_compressed_streaming")

  javaGrpcTests()
  akkaHttpGrpcTests(implicit mat => implicit ec => TestServiceServiceHandler(new TestServiceImpl()))
}
