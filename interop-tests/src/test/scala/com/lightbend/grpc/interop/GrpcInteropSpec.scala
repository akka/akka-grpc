package com.lightbend.grpc.interop

import akka.http.grpc.Grpc
import org.scalatest._

class GrpcInteropSpec extends WordSpec with GrpcInteropTests {
  javaGrpcTests()
  akkaHttpGrpcTests(implicit mat => implicit ec => Grpc(TestService.descriptor, new TestServiceImpl()))
}
