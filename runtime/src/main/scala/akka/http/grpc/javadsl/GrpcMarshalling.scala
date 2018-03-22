package akka.http.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }

import io.grpc.Status
import akka.NotUsed
import akka.http.grpc.GrpcServiceException
import akka.http.scaladsl.model.HttpEntity.{ ChunkStreamPart, LastChunk }
import akka.http.scaladsl.model.{ HttpEntity => SHttpEntity, HttpResponse => SHttpResponse }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.javadsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import akka.stream.javadsl.{ Sink, Source }
import akka.stream.scaladsl.{ Source => SSource }
import akka.http.grpc._

object GrpcMarshalling {
  def unmarshal[T](req: HttpRequest, u: ProtobufSerializer[T], mat: Materializer): CompletionStage[T] =
    (req.entity.getDataBytes via Grpc.grpcFramingDecoder).map(u.deserialize).runWith(Sink.head[T], mat)

  def unmarshalStream[T](req: HttpRequest, u: ProtobufSerializer[T], mat: Materializer): CompletionStage[Source[T, NotUsed]] = {
    CompletableFuture.completedFuture(
      req.entity.getDataBytes
        .mapMaterializedValue(_ ⇒ NotUsed)
        .via(Grpc.grpcFramingDecoder)
        .map(u.deserialize))
  }

  def marshal[T](e: T, m: ProtobufSerializer[T], mat: Materializer): HttpResponse =
    marshalStream(Source.single(e), m, mat)

  def marshalStream[T](e: Source[T, _], m: ProtobufSerializer[T], mat: Materializer): HttpResponse = {
    val outChunks = (e.asScala.map(m.serialize) via Grpc.grpcFramingEncoder)
      .map(bytes ⇒ SHttpEntity.Chunk(bytes).asInstanceOf[ChunkStreamPart])
      .concat(SSource.single(trailer(Status.OK)))
      .recover {
        case e: GrpcServiceException =>
          trailer(e.status)
        case e: Exception =>
          // TODO handle better
          e.printStackTrace()
          trailer(Status.UNKNOWN.withCause(e).withDescription("Stream failed"))
      }

    SHttpResponse(entity = SHttpEntity.Chunked(Grpc.contentType, outChunks))
  }

  def status(status: Status): HttpResponse =
    SHttpResponse(entity = SHttpEntity.Chunked(Grpc.contentType, SSource.single(trailer(status))))

  private def trailer(status: Status): LastChunk =
    LastChunk(trailer = List(RawHeader("grpc-status", status.getCode.value.toString)) ++ Option(status.getDescription).map(RawHeader("grpc-message", _)))

}
