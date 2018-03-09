package com.lightbend.grpc.interop

import io.grpc.testing.integration.test.{ AkkaGrpcClientTester, TestServiceHandler }
import io.grpc.testing.integration.TestServiceHandlerFactory
import io.grpc.testing.integration2.{ ClientTester, Settings }

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._

import io.grpc.testing.integration.test.TestService

import org.scalatest._

class GrpcInteropSpec extends WordSpec with GrpcInteropTests with Directives {

  //  grpcTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
  //  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
  //  grpcTests(AkkaHttpServerProviderScala, AkkaHttpClientProvider)

  //  grpcTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
  //  grpcTests(AkkaHttpServerProviderJava, AkkaHttpClientProvider)

  object AkkaHttpServerProviderScala extends AkkaHttpServerProvider {
    val label: String = "akka-grpc server scala"
    val server = AkkaGrpcServerScala(implicit mat => implicit sys => {
      implicit val ec = mat.executionContext

      val impl = TestServiceHandler(new TestServiceImpl())

      val route: Route = (pathPrefix(TestService.name) & extractRequest) { request ⇒
        val initalHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-initial")
        val trailingHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-trailing-bin")

        complete(impl(request).map(response ⇒
          HttpResponse(
            response.status,
            response.headers ++ initalHeaderToEcho,
            (trailingHeaderToEcho, response.entity) match {
              case (Some(hdr), HttpEntity.Chunked(contentType, data)) ⇒
                HttpEntity.Chunked(contentType, data.map {
                  case chunk: HttpEntity.Chunk ⇒ chunk
                  case last: HttpEntity.LastChunk ⇒ HttpEntity.LastChunk(last.extension, last.trailer ++ trailingHeaderToEcho)
                })
              case _ ⇒
                response.entity
            },
            response.protocol)))
      }

      Route.asyncHandler(Route.seal(route))
    })

  }

  object AkkaHttpServerProviderJava extends AkkaHttpServerProvider {
    val label: String = "akka-grpc server java"
    val server = new AkkaGrpcServerJava(mat ⇒ {
      TestServiceHandlerFactory.create(new JavaTestServiceImpl(mat), mat)
    })
  }

  object AkkaHttpClientProvider extends AkkaHttpClientProvider {
    def client = AkkaGrpcClientScala(settings => implicit mat => implicit ec => new AkkaGrpcClientTester(settings))
  }

}
