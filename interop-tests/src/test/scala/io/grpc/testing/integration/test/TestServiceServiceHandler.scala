

package io.grpc.testing.integration.test

import io.grpc.testing.integration.Messages

import com.google.protobuf.EmptyProtos

import scala.concurrent.{ ExecutionContext, Future }

import akka.http.grpc.{ GrpcExceptionHandler, GrpcMarshalling }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.stream.Materializer

import com.lightbend.grpc.interop.GoogleProtobufSerializer.googlePbSerializer

object TestServiceServiceHandler {
  def apply(implementation: TestServiceService)(implicit mat: Materializer): PartialFunction[HttpRequest, Future[HttpResponse]] = {
    implicit val ec: ExecutionContext = mat.executionContext

    val SimpleRequestSerializer = googlePbSerializer[Messages.SimpleRequest]

    val StreamingOutputCallRequestSerializer = googlePbSerializer[Messages.StreamingOutputCallRequest]

    val EmptySerializer = googlePbSerializer[EmptyProtos.Empty]

    val SimpleResponseSerializer = googlePbSerializer[Messages.SimpleResponse]

    val StreamingInputCallRequestSerializer = googlePbSerializer[Messages.StreamingInputCallRequest]

    val StreamingInputCallResponseSerializer = googlePbSerializer[Messages.StreamingInputCallResponse]

    val StreamingOutputCallResponseSerializer = googlePbSerializer[Messages.StreamingOutputCallResponse]

    def handle(request: HttpRequest, method: String): Future[HttpResponse] = method match {

      case "EmptyCall" =>
        GrpcMarshalling.unmarshal(request, EmptySerializer, mat)
          .flatMap(implementation.emptyCall(_))
          .map(e => GrpcMarshalling.marshal(e, EmptySerializer, mat))

      case "UnaryCall" =>
        GrpcMarshalling.unmarshal(request, SimpleRequestSerializer, mat)
          .flatMap(implementation.unaryCall(_))
          .map(e => GrpcMarshalling.marshal(e, SimpleResponseSerializer, mat))

      case "CacheableUnaryCall" =>
        GrpcMarshalling.unmarshal(request, SimpleRequestSerializer, mat)
          .flatMap(implementation.cacheableUnaryCall(_))
          .map(e => GrpcMarshalling.marshal(e, SimpleResponseSerializer, mat))

      case "StreamingOutputCall" =>
        GrpcMarshalling.unmarshal(request, StreamingOutputCallRequestSerializer, mat)
          .map(implementation.streamingOutputCall(_))
          .map(e => GrpcMarshalling.marshalStream(e, StreamingOutputCallResponseSerializer, mat))

      case "StreamingInputCall" =>
        GrpcMarshalling.unmarshalStream(request, StreamingInputCallRequestSerializer, mat)
          .flatMap(implementation.streamingInputCall(_))
          .map(e => GrpcMarshalling.marshal(e, StreamingInputCallResponseSerializer, mat))

      case "FullDuplexCall" =>
        GrpcMarshalling.unmarshalStream(request, StreamingOutputCallRequestSerializer, mat)
          .map(implementation.fullDuplexCall(_))
          .map(e => GrpcMarshalling.marshalStream(e, StreamingOutputCallResponseSerializer, mat))

      case "HalfDuplexCall" =>
        GrpcMarshalling.unmarshalStream(request, StreamingOutputCallRequestSerializer, mat)
          .map(implementation.halfDuplexCall(_))
          .map(e => GrpcMarshalling.marshalStream(e, StreamingOutputCallResponseSerializer, mat))

      case "UnimplementedCall" =>
        GrpcMarshalling.unmarshal(request, EmptySerializer, mat)
          .flatMap(implementation.unimplementedCall(_))
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