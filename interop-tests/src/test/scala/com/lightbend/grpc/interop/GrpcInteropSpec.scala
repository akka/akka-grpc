package com.lightbend.grpc.interop

import io.grpc.testing.integration.test.{ AkkaGrpcClientTester, TestServiceServiceHandler }
import io.grpc.testing.integration.TestServiceServiceHandlerFactory
import io.grpc.testing.integration2.{ ClientTester, Settings }
import org.scalatest._

class GrpcInteropSpec extends WordSpec with GrpcInteropTests {

  grpcTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderScala, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderJava, AkkaHttpClientProvider)

  object AkkaHttpServerProviderScala extends AkkaHttpServerProvider {
    val label: String = "akka-grpc server scala"
    val server = AkkaGrpcServerScala(implicit mat => implicit ec => TestServiceServiceHandler(new TestServiceImpl()))
  }

  object AkkaHttpServerProviderJava extends AkkaHttpServerProvider {
    val label: String = "akka-grpc server java"
    val server = new AkkaGrpcServerJava(mat ⇒ {
      TestServiceServiceHandlerFactory.create(new JavaTestServiceImpl(mat), mat)
    })
  }

  object AkkaHttpClientProvider extends AkkaHttpClientProvider {
    def client = AkkaGrpcClientScala(settings => implicit mat => implicit ec => new AkkaGrpcClientTester(settings))
  }

}
