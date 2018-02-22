package com.lightbend.grpc.interop

import akka.http.grpc.Grpc

import org.scalatest.WordSpec

class GrpcInteropSpec extends WordSpec with GrpcInteropTests {
  akkaHttpGrpcTests(implicit mat => implicit ec => Grpc(TestServiceImpl.descriptor, new TestServiceImpl()))
}
