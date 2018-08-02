/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.grpc.interop

import akka.NotUsed
import akka.grpc.scaladsl.GrpcMarshalling
import akka.grpc.Identity
import akka.grpc.internal.GrpcResponseHelpers
import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }
import io.grpc.testing.integration.test.{ AkkaGrpcScalaClientTester, TestService, TestServiceHandler }
import io.grpc.testing.integration.{ AkkaGrpcJavaClientTester, TestServiceHandlerFactory }
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{ FromRequestUnmarshaller, Unmarshaller }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status
import io.grpc.testing.integration.messages._
import io.grpc.testing.integration.test.{ TestService, TestServiceMarshallers }
import org.scalatest._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future, Promise }

class GrpcInteropSpec extends WordSpec with GrpcInteropTests with Directives {

  grpcTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProviderScala)
  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProviderJava)

  grpcTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderScala, AkkaHttpClientProviderScala)
  grpcTests(AkkaHttpServerProviderScala, AkkaHttpClientProviderJava)

  grpcTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderJava, AkkaHttpClientProviderScala)
  grpcTests(AkkaHttpServerProviderJava, AkkaHttpClientProviderJava)

  object AkkaHttpServerProviderScala extends AkkaHttpServerProvider with Directives {
    val label: String = "akka-grpc server scala"
    val pendingCases =
      Set(

      )

    val server = AkkaGrpcServerScala(implicit mat => implicit sys => {
      implicit val ec = mat.executionContext

      val testServiceImpl = new TestServiceImpl()
      val testServiceHandler = TestServiceHandler(testServiceImpl)

      val route: Route = (pathPrefix(TestService.name) & echoHeaders) {
        handleWith(testServiceHandler)
        //  The "status_code_and_message" test can be solved either using the 'customStatusRoute' here or
        //  throwing an exception on the service code  and handling it on the appropriate GrpcMarshalling
        //  handler as demoed in 'TestServiceImpl'.
        //  customStatusRoute(testServiceImpl) ~ handleWith(testServiceHandler)
      }

      Route.asyncHandler(Route.seal(route))
    })

    // Directive to implement the 'custom_metadata' test
    val echoHeaders: Directive0 = extractRequest.flatMap(request ⇒ {
      val initialHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-initial")
      val trailingHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-trailing-bin")

      mapResponseHeaders(h ⇒ h ++ initialHeaderToEcho) & mapTrailingResponseHeaders(h ⇒ h ++ trailingHeaderToEcho)
    })

    // Route to pass the 'status_code_and_message' test
    def customStatusRoute(testServiceImpl: TestServiceImpl)(implicit mat: Materializer): Route = {
      implicit val ec = mat.executionContext
      // TODO add a directive that gets the codec from the headers?
      // or a directive that wraps request and response and compresses in post-processing?
      implicit val codec = Identity

      import TestServiceMarshallers._

      pathPrefix("UnaryCall") {
        entity(as[SimpleRequest]) { req ⇒
          val simpleResponse = testServiceImpl.unaryCall(req)

          req.responseStatus match {
            case None ⇒
              complete(simpleResponse)
            case Some(responseStatus) ⇒
              mapTrailingResponseHeaders(_ ⇒ GrpcResponseHelpers.statusHeaders(Status.fromCodeValue(responseStatus.code).withDescription(responseStatus.message))) {
                complete(simpleResponse)
              }
          }

        }
      } ~ pathPrefix("FullDuplexCall") {
        entity(as[Source[StreamingOutputCallRequest, NotUsed]]) { source ⇒

          val status = Promise[Status]

          val effectingSource = source.map { requestElement ⇒
            requestElement.responseStatus match {
              case None ⇒
                status.trySuccess(Status.OK)
              case Some(responseStatus) ⇒
                status.trySuccess(Status.fromCodeValue(responseStatus.code).withDescription(responseStatus.message))
            }
            requestElement
          }.watchTermination()((NotUsed, f) ⇒ {
            f.foreach(_ ⇒ status.trySuccess(Status.OK))
            NotUsed
          })

          complete(GrpcResponseHelpers(testServiceImpl.fullDuplexCall(effectingSource), status.future))
        }
      }
    }

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
    val label: String = "akka-grpc java server"

    val pendingCases =
      Set(
        "custom_metadata",
      )

    val server = new AkkaGrpcServerJava(mat ⇒ {
      TestServiceHandlerFactory.create(new JavaTestServiceImpl(mat), mat)
    })
  }

  object AkkaHttpClientProviderScala extends AkkaHttpClientProvider {
    val label: String = "akka-grpc scala client tester"

    def client = AkkaGrpcClientScala(settings => implicit mat => implicit as => new AkkaGrpcScalaClientTester(settings))
  }

  object AkkaHttpClientProviderJava extends AkkaHttpClientProvider {
    val label: String = "akka-grpc java client tester"

    def client = new AkkaGrpcClientJava((settings, mat, as) => new AkkaGrpcJavaClientTester(settings, mat, as))
  }

}
