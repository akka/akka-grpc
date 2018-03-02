package com.lightbend.grpc.interop

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import io.grpc.testing.integration.test.{ AkkaGrpcClientTester, TestServiceServiceHandler }
import io.grpc.testing.integration2.{ ClientTester, Settings }
import org.scalatest._

import scala.concurrent.{ ExecutionContext, Future }

class GrpcInteropSpec extends WordSpec with GrpcInteropTests {

  javaGrpcTests(GrpcJavaClientTesterProvider)
  javaGrpcTests(AkkaHttpClientProvider)

  akkaGrpcTests(AkkaHttpServerProvider, GrpcJavaClientTesterProvider)
  akkaGrpcTests(AkkaHttpServerProvider, AkkaHttpClientProvider)

  object AkkaHttpServerProvider extends AkkaHttpServerProvider {
    val serverHandlerFactory: Materializer => ExecutionContext => PartialFunction[HttpRequest, Future[HttpResponse]] =
      implicit mat => implicit ec => TestServiceServiceHandler(new TestServiceImpl())
  }

  object AkkaHttpClientProvider extends AkkaClientTestProvider {
    val clientTesterFactory: Settings => ExecutionContext => ClientTester =
      settings => implicit ec => new AkkaGrpcClientTester(settings)
  }

}
