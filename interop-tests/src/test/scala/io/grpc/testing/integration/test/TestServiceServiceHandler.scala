

package io.grpc.testing.integration.test

import akka.http.grpc.{ GrpcExceptionHandler, GrpcMarshalling }
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import io.grpc.testing.integration.test.TestServiceService._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Hard-coded service handler to help us define what needs to be the final generated service handler.
  */
object TestServiceServiceHandler {
  def apply(implementation: TestServiceService)(implicit mat: Materializer): PartialFunction[HttpRequest, Future[HttpResponse]] = {
    implicit val ec: ExecutionContext = mat.executionContext

    def handle(request: HttpRequest, method: String): Future[HttpResponse] = method match {

      case "EmptyCall" =>
        GrpcMarshalling.unmarshal(request, EmptySerializer, mat)
          .flatMap(implementation.emptyCall)
          .map(e => GrpcMarshalling.marshal(e, EmptySerializer, mat))

      case "UnaryCall" =>
        GrpcMarshalling.unmarshal(request, SimpleRequestSerializer, mat)
          .flatMap(implementation.unaryCall)
          .map(e => GrpcMarshalling.marshal(e, SimpleResponseSerializer, mat))

      case "CacheableUnaryCall" =>
        GrpcMarshalling.unmarshal(request, SimpleRequestSerializer, mat)
          .flatMap(implementation.cacheableUnaryCall)
          .map(e => GrpcMarshalling.marshal(e, SimpleResponseSerializer, mat))

      case "StreamingOutputCall" =>
        GrpcMarshalling.unmarshal(request, StreamingOutputCallRequestSerializer, mat)
          .map(implementation.streamingOutputCall)
          .map(e => GrpcMarshalling.marshalStream(e, StreamingOutputCallResponseSerializer, mat))

      case "StreamingInputCall" =>
        GrpcMarshalling.unmarshalStream(request, StreamingInputCallRequestSerializer, mat)
          .flatMap(implementation.streamingInputCall)
          .map(e => GrpcMarshalling.marshal(e, StreamingInputCallResponseSerializer, mat))

      case "FullDuplexCall" =>
        GrpcMarshalling.unmarshalStream(request, StreamingOutputCallRequestSerializer, mat)
          .map(implementation.fullDuplexCall)
          .map(e => GrpcMarshalling.marshalStream(e, StreamingOutputCallResponseSerializer, mat))

      case "HalfDuplexCall" =>
        GrpcMarshalling.unmarshalStream(request, StreamingOutputCallRequestSerializer, mat)
          .map(implementation.halfDuplexCall)
          .map(e => GrpcMarshalling.marshalStream(e, StreamingOutputCallResponseSerializer, mat))

      case "UnimplementedCall" =>
        GrpcMarshalling.unmarshal(request, EmptySerializer, mat)
          .flatMap(implementation.unimplementedCall)
          .map(e => GrpcMarshalling.marshal(e, EmptySerializer, mat))

      case m =>
        Future.failed(new NotImplementedError(s"Not implemented: $m"))
    }

    Function.unlift((req: HttpRequest) => req.uri.path match {
      case Path.Slash(Segment(TestServiceService.name, Path.Slash(Segment(method, Path.Empty)))) ⇒
        Some(handle(req, method).recoverWith(GrpcExceptionHandler.default))
      case _ => None
    })
  }
}