package com.lightbend.grpc.interop

import io.grpc.testing.integration.test.{ AkkaGrpcClientTester, TestServiceServiceHandler }
import org.scalatest._

class GrpcInteropSpec extends WordSpec with GrpcInteropTests {

  grpcTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProvider, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProvider, AkkaHttpClientProvider)

  object AkkaHttpServerProvider extends AkkaHttpServerProvider {
    val server = AkkaGrpcServerScala(implicit mat => implicit ec => TestServiceServiceHandler(new TestServiceImpl()))
  }

  object AkkaHttpClientProvider extends AkkaHttpClientProvider {
    def client = AkkaGrpcClientScala(settings => implicit mat => implicit ec => new AkkaGrpcClientTester(settings))
  }

}
