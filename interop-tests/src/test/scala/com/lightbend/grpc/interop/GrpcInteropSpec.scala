package com.lightbend.grpc.interop

import io.grpc.testing.integration.test.{ AkkaGrpcClientTester, TestServiceServiceHandler }
import io.grpc.testing.integration2.{ ClientTester, Settings }
import org.scalatest._

import scala.concurrent.ExecutionContext

class GrpcInteropSpec extends WordSpec with GrpcInteropTests {

  grpcTests(IoGrpcJavaServer, GrpcJavaClientTesterProvider)
  grpcTests(IoGrpcJavaServer, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProvider, GrpcJavaClientTesterProvider)
  grpcTests(AkkaHttpServerProvider, AkkaHttpClientProvider)

  object AkkaHttpServerProvider extends AkkaHttpServerProvider {
    val server = AkkaGrpcServerScala(implicit mat => implicit ec => TestServiceServiceHandler(new TestServiceImpl()))
  }

  object AkkaHttpClientProvider extends AkkaClientTestProvider {
    val clientTesterFactory: Settings => ExecutionContext => ClientTester =
      settings => implicit ec => new AkkaGrpcClientTester(settings)
  }

}
