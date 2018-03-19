package com.lightbend.grpc.interop

import akka.NotUsed
import akka.http.grpc.{ GrpcMarshalling, GrpcResponse }
import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }
import io.grpc.testing.integration.test.{ AkkaGrpcClientTester, TestServiceHandler }
import io.grpc.testing.integration.TestServiceHandlerFactory
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{ FromRequestUnmarshaller, Unmarshaller }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status
import io.grpc.testing.integration.messages.{ SimpleRequest, SimpleResponse, StreamingOutputCallRequest, StreamingOutputCallResponse }
import io.grpc.testing.integration.test.TestService
import org.scalatest._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Promise }

class GrpcInteropSpec extends WordSpec with GrpcInteropTests with Directives {

  grpcTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderScala, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderJava, AkkaHttpClientProvider)

  object AkkaHttpServerProviderScala extends AkkaHttpServerProvider with Directives {
    val label: String = "akka-grpc server scala"
    val pendingCases =
      Set(
        "client_compressed_unary",
        "client_compressed_streaming")

    val server = AkkaGrpcServerScala(implicit mat => implicit sys => {
      implicit val ec = mat.executionContext

      val testServiceImpl = new TestServiceImpl()
      val testServiceHandler = TestServiceHandler(testServiceImpl)

      val route: Route = (pathPrefix(TestService.name) & echoHeaders) {
        handleWith(testServiceHandler)
      }

      Route.asyncHandler(Route.seal(route))
    })

    // Directive to implement the 'custom_metadata' test
    val echoHeaders: Directive0 = extractRequest.flatMap(request ⇒ {
      val initialHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-initial")
      val trailingHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-trailing-bin")

      mapResponseHeaders(h ⇒ h ++ initialHeaderToEcho) & mapTrailingResponseHeaders(h ⇒ h ++ trailingHeaderToEcho)
    })

    // TODO move to runtime library or even akka-http
    def mapTrailingResponseHeaders(f: immutable.Seq[HttpHeader] ⇒ immutable.Seq[HttpHeader]): Directive0 =
      mapResponse(response ⇒
        response.withEntity(response.entity match {
          case HttpEntity.Chunked(contentType, data) ⇒ {
            HttpEntity.Chunked(contentType, data.map {
              case chunk: HttpEntity.Chunk ⇒ chunk
              case last: HttpEntity.LastChunk ⇒
                HttpEntity.LastChunk(last.extension, f(last.trailer))
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
