/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.internal.{ GrpcEntityHelpers, GrpcProtocolNative, GrpcResponseHelpers, Identity }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ AttributeKeys, HttpEntity, HttpHeader, Trailer }
import akka.http.scaladsl.server.{ Directive0, Directives, Route }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status
import io.grpc.testing.integration.messages.{ SimpleRequest, StreamingOutputCallRequest }
import io.grpc.testing.integration.test.{ TestService, TestServiceHandler, TestServiceMarshallers }

import scala.collection.immutable
import scala.concurrent.Promise

object AkkaHttpServerProviderScala extends AkkaHttpServerProvider with Directives {
  val label: String = "akka-grpc server scala"
  val pendingCases =
    Set()

  val server = AkkaGrpcServerScala(implicit sys => {
    val testServiceImpl = new TestServiceImpl()
    val testServiceHandler = TestServiceHandler(testServiceImpl)

    val route: Route = (pathPrefix(TestService.name) & echoHeaders) {
      handleWith(testServiceHandler)
      //  The "status_code_and_message" test can be solved either using the 'customStatusRoute' here or
      //  throwing an exception on the service code  and handling it on the appropriate GrpcMarshalling
      //  handler as demoed in 'TestServiceImpl'.
      //  customStatusRoute(testServiceImpl) ~ handleWith(testServiceHandler)
    }

    Route.toFunction(Route.seal(route))
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
    implicit val writer = GrpcProtocolNative.newWriter(Identity)

    import TestServiceMarshallers._

    pathPrefix("UnaryCall") {
      entity(as[SimpleRequest]) { req =>
        val simpleResponse = testServiceImpl.unaryCall(req)

        req.responseStatus match {
          case None =>
            complete(simpleResponse)
          case Some(responseStatus) =>
            mapTrailingResponseHeaders(_ =>
              GrpcEntityHelpers.statusHeaders(
                Status.fromCodeValue(responseStatus.code).withDescription(responseStatus.message))) {
              complete(simpleResponse)
            }
        }
      }
    } ~ pathPrefix("FullDuplexCall") {
      entity(as[Source[StreamingOutputCallRequest, NotUsed]]) { source =>
        val status = Promise[Status]()

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
      response.entity match {
        case HttpEntity.Chunked(contentType, data) =>
          response.withEntity(
            HttpEntity.Chunked(
              contentType,
              data.map {
                case chunk: HttpEntity.Chunk => chunk
                case last: HttpEntity.LastChunk =>
                  HttpEntity.LastChunk(last.extension, f(last.trailer))
              }))
        case _ =>
          val origTrailers = response
            .attribute(AttributeKeys.trailer)
            .map(_.headers)
            .getOrElse(Vector.empty)
            .map(e => RawHeader(e._1, e._2))
          response.addAttribute(AttributeKeys.trailer, Trailer(f(origTrailers)))
      })
}
