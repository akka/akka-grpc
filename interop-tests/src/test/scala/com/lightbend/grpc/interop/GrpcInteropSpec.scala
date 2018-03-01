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

    val pendingCases =
      Set(
        "ping_pong",
        "empty_stream",
        "client_streaming",
        "server_streaming",
        "cancel_after_begin",
        "cancel_after_first_response",
        "timeout_on_sleeping_server",
        "custom_metadata",
        "status_code_and_message",
        "client_compressed_unary",
        "client_compressed_streaming",
        "server_compressed_unary",
        "server_compressed_streaming",
        "unimplemented_service",
      )

    val clientTesterFactory: Settings => ClientTester = settings => new AkkaGrpcClientTester(settings)
  }

}
