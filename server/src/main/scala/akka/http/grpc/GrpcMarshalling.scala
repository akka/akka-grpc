package akka.http.grpc

import scala.concurrent.Future

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

  def marshalStream[T](e: Source[T, _], m: ProtobufSerializer[T], mat: Materializer): HttpResponse = {
    val outChunks = (e.map(m.serialize) via Grpc.grpcFramingEncoder)
      .map(bytes ⇒ HttpEntity.Chunk(bytes))
      .concat(Source.single(LastChunk(trailer = List(RawHeader("grpc-status", "0")))))
      .recover {
        case e: Exception =>
          // TODO handle better
          e.printStackTrace()
          LastChunk(trailer = List(RawHeader("grpc-status", "2"), RawHeader("grpc-message", "Stream error")))
      }

    HttpResponse(entity = HttpEntity.Chunked(Grpc.contentType, outChunks))
  }
}
