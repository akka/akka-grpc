/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.Identity
import akka.grpc.internal.GrpcResponseHelpers
import akka.grpc.scaladsl.GrpcMarshalling
import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }
import akka.http.scaladsl.model.{ HttpEntity, HttpHeader, HttpRequest }
import akka.http.scaladsl.server.{ Directive0, Directives, Route }
import akka.http.scaladsl.unmarshalling.{ FromRequestUnmarshaller, Unmarshaller }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status
import io.grpc.testing.integration.messages.{ SimpleRequest, SimpleResponse, StreamingOutputCallRequest }
import io.grpc.testing.integration.test.{ TestService, TestServiceHandler, TestServiceMarshallers }

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Promise }

object AkkaHttpServerProviderScala extends AkkaHttpServerProvider with Directives {
  val label: String = "akka-grpc server scala"
  val pendingCases =
    Set()

  val server = AkkaGrpcServerScala(implicit mat =>
    implicit sys => {
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
  val echoHeaders: Directive0 = extractRequest.flatMap(request => {
    val initialHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-initial")
    val trailingHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-trailing-bin")

    mapResponseHeaders(h => h ++ initialHeaderToEcho) & mapTrailingResponseHeaders(h => h ++ trailingHeaderToEcho)
  })

  // Route to pass the 'status_code_and_message' test
  def customStatusRoute(testServiceImpl: TestServiceImpl)(implicit mat: Materializer, system: ActorSystem): Route = {
    implicit val ec = mat.executionContext
    implicit val codec = Identity

    import TestServiceMarshallers._

    pathPrefix("UnaryCall") {
      entity(as[SimpleRequest]) { req =>
        val simpleResponse = testServiceImpl.unaryCall(req)

        req.responseStatus match {
          case None =>
            complete(simpleResponse)
          case Some(responseStatus) =>
            mapTrailingResponseHeaders(
              _ =>
                GrpcResponseHelpers.statusHeaders(
                  Status.fromCodeValue(responseStatus.code).withDescription(responseStatus.message))) {
              complete(simpleResponse)
            }
        }
      }
    } ~ pathPrefix("FullDuplexCall") {
      entity(as[Source[StreamingOutputCallRequest, NotUsed]]) { source =>
        val status = Promise[Status]

        val effectingSource = source
          .map { requestElement =>
            requestElement.responseStatus match {
              case None =>
                status.trySuccess(Status.OK)
              case Some(responseStatus) =>
                status.trySuccess(Status.fromCodeValue(responseStatus.code).withDescription(responseStatus.message))
            }
            requestElement
          }
          .watchTermination()((NotUsed, f) => {
            f.foreach(_ => status.trySuccess(Status.OK))
            NotUsed
          })

        complete(GrpcResponseHelpers(testServiceImpl.fullDuplexCall(effectingSource), status.future))
      }
    }
  }

  // TODO move to runtime library or even akka-http
  def mapTrailingResponseHeaders(f: immutable.Seq[HttpHeader] => immutable.Seq[HttpHeader]): Directive0 =
    mapResponse(response =>
      response.withEntity(response.entity match {
        case HttpEntity.Chunked(contentType, data) => {
          HttpEntity.Chunked(contentType, data.map {
            case chunk: HttpEntity.Chunk => chunk
            case last: HttpEntity.LastChunk =>
              HttpEntity.LastChunk(last.extension, f(last.trailer))
          })
        }
        case _ =>
          throw new IllegalArgumentException("Trailing response headers are only supported on Chunked responses")
      }))
}
