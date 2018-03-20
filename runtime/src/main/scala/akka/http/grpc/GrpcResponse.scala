package akka.http.grpc

import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.{ HttpEntity, HttpResponse }
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status

import scala.concurrent.Future

/**
 * Some helpers for creating HTTP responses for use with gRPC
 */
object GrpcResponse {
  def apply[T](e: Source[T, NotUsed])(implicit m: ProtobufSerializer[T], mat: Materializer): HttpResponse =
    GrpcResponse(e, Source.single(trailer(Status.OK)))

  def apply[T](e: Source[T, NotUsed], status: Future[Status])(implicit m: ProtobufSerializer[T], mat: Materializer): HttpResponse = {
    implicit val ec = mat.executionContext
    GrpcResponse(
      e,
      Source
        .lazilyAsync(() ⇒ status.map(trailer(_)))
        .mapMaterializedValue(_ ⇒ NotUsed))
  }

  def apply[T](e: Source[T, NotUsed], trail: Source[HttpEntity.LastChunk, NotUsed])(implicit m: ProtobufSerializer[T], mat: Materializer): HttpResponse = {
    val outChunks = e
      .map(m.serialize)
      .via(Grpc.grpcFramingEncoder)
      .map(bytes ⇒ HttpEntity.Chunk(bytes))
      .concat(trail)
      .recover {
        case e: GrpcServiceException =>
          trailer(e.status)
        case e: Exception =>
          // TODO handle better
          e.printStackTrace()
          trailer(Status.UNKNOWN.withCause(e).withDescription("Stream failed"))
      }

    HttpResponse(entity = HttpEntity.Chunked(Grpc.contentType, outChunks))
  }

  def status(status: Status): HttpResponse =
    HttpResponse(entity = HttpEntity.Chunked(Grpc.contentType, Source.single(trailer(status))))

  def trailer(status: Status): LastChunk =
    LastChunk(trailer = statusHeaders(status))

  def statusHeaders(status: Status): List[RawHeader] =
    List(RawHeader("grpc-status", status.getCode.value.toString)) ++ Option(status.getDescription).map(RawHeader("grpc-message", _))

}
