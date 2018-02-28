package akka.http.grpc

import java.io.{ ByteArrayInputStream, InputStream }

import scala.concurrent.Future
import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.google.protobuf.CodedInputStream
import com.trueaccord.scalapb.GeneratedMessage
import io.grpc.Status

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
      .concat(Source.single(trailer(Status.OK)))
      .recover {
        case e: Exception =>
          // TODO handle better
          e.printStackTrace()
          trailer(Status.UNKNOWN.withCause(e).withDescription("Stream failed"))
      }

    HttpResponse(entity = HttpEntity.Chunked(Grpc.contentType, outChunks))
  }

  def status(status: Status): HttpResponse =
    HttpResponse(entity = HttpEntity.Chunked(Grpc.contentType, Source.single(trailer(status))))

  private def trailer(status: Status): LastChunk =
    LastChunk(trailer = List(RawHeader("grpc-status", status.getCode.value.toString)) ++ Option(status.getDescription).map(RawHeader("grpc-message", _)))

  // TODO move to client part once ProtobufSerializer is moved to "runtime" library
  class Marshaller[T <: com.trueaccord.scalapb.GeneratedMessage](u: ProtobufSerializer[T]) extends io.grpc.MethodDescriptor.Marshaller[T] {
    override def parse(stream: InputStream): T = {
      val bytes = Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray
      u.deserialize(akka.util.ByteString(bytes))
    }
    override def stream(value: T): InputStream = new ByteArrayInputStream(value.toByteArray)
  }
}
