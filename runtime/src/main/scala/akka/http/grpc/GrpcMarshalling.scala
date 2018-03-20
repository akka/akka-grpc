package akka.http.grpc

import scala.concurrent.Future

import io.grpc.Status

import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

object GrpcMarshalling {
  def unmarshal[T](req: HttpRequest, u: ProtobufSerializer[T], mat: Materializer): Future[T] =
    (req.entity.dataBytes via Grpc.grpcFramingDecoder).map(u.deserialize).runWith(Sink.head)(mat)

  def unmarshalStream[T](req: HttpRequest, u: ProtobufSerializer[T], mat: Materializer): Future[Source[T, NotUsed]] = {
    Future.successful(
      req.entity.dataBytes
        .mapMaterializedValue(_ ⇒ NotUsed)
        .via(Grpc.grpcFramingDecoder)
        .map(u.deserialize))
  }

  def marshal[T](e: T, m: ProtobufSerializer[T], mat: Materializer): HttpResponse =
    marshalStream(Source.single(e), m, mat)

  def marshalStream[T](e: Source[T, NotUsed], m: ProtobufSerializer[T], mat: Materializer): HttpResponse =
    GrpcResponse(e)(m, mat)

  def status(status: Status): HttpResponse =
    HttpResponse(entity = HttpEntity.Chunked(Grpc.contentType, Source.single(trailer(status))))

  private def trailer(status: Status): LastChunk =
    LastChunk(trailer = List(RawHeader("grpc-status", status.getCode.value.toString)) ++ Option(status.getDescription).map(RawHeader("grpc-message", _)))

}
