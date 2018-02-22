package akka.http.grpc

import scala.concurrent.{ ExecutionContext, Future }

import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse }
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

// TODO should move to runtime
object GrpcRuntimeMarshalling {
  def unmarshall[T](req: HttpRequest, u: ProtobufSerializer[T], mat: Materializer): Future[T] =
    (req.entity.dataBytes via Grpc.grpcFramingDecoder).map(u.deserialize).runWith(Sink.head)(mat)

  def unmarshallStream[T](req: HttpRequest, u: ProtobufSerializer[T]): Source[T, Any] =
    (req.entity.dataBytes via Grpc.grpcFramingDecoder).map(u.deserialize)

  def marshal[T](e: T, m: ProtobufSerializer[T], mat: Materializer): HttpResponse =
    marshalStream(Source.single(e), m, mat)

  def marshalStream[T](e: Source[T, NotUsed], m: ProtobufSerializer[T], mat: Materializer): HttpResponse = {
    val outChunks = (e.map(m.serialize) via Grpc.grpcFramingEncoder)
      .map(bytes ⇒ HttpEntity.Chunk(bytes))
      .concat(Source.single(LastChunk(trailer = List(RawHeader("grpc-status", "0")))))
      .recover {
        case e: Exception =>
          // todo handle better
          e.printStackTrace()
          LastChunk(trailer = List(RawHeader("grpc-status", "2"), RawHeader("grpc-message", "Stream error")))
      }

    HttpResponse(entity = HttpEntity.Chunked(Grpc.contentType, outChunks))
  }
}
