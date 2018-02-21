package akka.http.grpc

import scala.concurrent.{ ExecutionContext, Future }

import akka.NotUsed
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.{ FromRequestUnmarshaller, Unmarshaller }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

// TODO should move to runtime
object GrpcRuntimeMarshallingImplicits {
  implicit def unmarshaller[T](implicit u: ProtobufSerializer[T], mat: Materializer): FromRequestUnmarshaller[T] =
    Unmarshaller { _: ExecutionContext ⇒ req: HttpRequest ⇒ (req.entity.dataBytes via Grpc.grpcFramingDecoder).map(u.deserialize).runWith(Sink.head)
    }
  implicit def streamUnmarshaller[T](implicit u: ProtobufSerializer[T]): FromRequestUnmarshaller[Source[T, Any]] =
    Unmarshaller { _: ExecutionContext ⇒ req: HttpRequest ⇒ Future.successful((req.entity.dataBytes via Grpc.grpcFramingDecoder).map(u.deserialize))
    }
  implicit def marshaller[T](e: T)(implicit m: ProtobufSerializer[T], mat: Materializer): ToResponseMarshallable =
    streamMarshaller(Source.single(e))

  implicit def streamMarshaller[T](e: Source[T, NotUsed])(implicit m: ProtobufSerializer[T], mat: Materializer): ToResponseMarshallable = {
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
