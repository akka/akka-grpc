package com.lightbend.grpc.interop

import io.grpc.testing.integration.test.{ AkkaGrpcClientTester, TestService, TestServiceHandler }
import io.grpc.testing.integration.TestServiceHandlerFactory
import io.grpc.testing.integration2.{ ClientTester, Settings }

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.RouteResult.Complete

import io.grpc.testing.integration.test.TestService

import org.scalatest._

import scala.collection.immutable

class GrpcInteropSpec extends WordSpec with GrpcInteropTests with Directives {

  grpcTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderScala, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderJava, AkkaHttpClientProvider)

  object AkkaHttpServerProviderScala extends AkkaHttpServerProvider {
    val label: String = "akka-grpc server scala"
    val pendingCases =
      Set(
        "status_code_and_message",
        "client_compressed_unary",
        "client_compressed_streaming")

    val server = AkkaGrpcServerScala(implicit mat => implicit sys => {
      implicit val ec = mat.executionContext

      val impl = TestServiceHandler(new TestServiceImpl())

      val route: Route = (pathPrefix(TestService.name) & echoHeaders & extractRequest) { request ⇒
        complete(impl(request))
      }

      Route.asyncHandler(Route.seal(route))
    })

    val echoHeaders: Directive0 = extractRequest.flatMap(request ⇒ {
      val initialHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-initial")
      val trailingHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-trailing-bin")

      mapResponseHeaders(h ⇒ h ++ initialHeaderToEcho) & mapTrailingResponseHeaders(h ⇒ h ++ trailingHeaderToEcho)
    })

    // TODO to be moved to the runtime lib (or even akka-http itself?)
    def mapTrailingResponseHeaders(f: immutable.Seq[HttpHeader] ⇒ immutable.Seq[HttpHeader]) =
      mapResponse(response ⇒
        response.withEntity(response.entity match {
          case HttpEntity.Chunked(contentType, data) ⇒ {
            HttpEntity.Chunked(contentType, data.map {
              case chunk: HttpEntity.Chunk ⇒ chunk
              case last: HttpEntity.LastChunk ⇒ HttpEntity.LastChunk(last.extension, f(last.trailer))
            })
          }
          case _ ⇒
            throw new IllegalArgumentException("Trailing response headers are only supported on Chunked responses")
        }))
  }

  object AkkaHttpServerProviderJava extends AkkaHttpServerProvider {
    val label: String = "akka-grpc server java"

    val pendingCases =
      Set(
        "custom_metadata",
        "status_code_and_message",
        "client_compressed_unary",
        "client_compressed_streaming")

    val server = new AkkaGrpcServerJava(mat ⇒ {
      TestServiceHandlerFactory.create(new JavaTestServiceImpl(mat), mat)
    })
  }

  object AkkaHttpClientProvider extends AkkaHttpClientProvider {
    def client = AkkaGrpcClientScala(settings => implicit mat => implicit ec => new AkkaGrpcClientTester(settings))
  }

}
