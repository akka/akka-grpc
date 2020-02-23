package akka.grpc.internal
import akka.actor.ActorSystem
import akka.grpc.{ Codecs, ProtobufSerializer }
import akka.grpc.GrpcProtocol.GrpcProtocolMarshaller
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.NotUsed
import akka.annotation.InternalApi
import akka.grpc.scaladsl.{ headers, GrpcExceptionHandler }
import akka.http.scaladsl.model.{ HttpEntity, HttpMethods, HttpRequest, Uri }
import io.grpc.Status

import scala.collection.immutable

@InternalApi
object GrpcRequestHelpers {

  def apply[T](
      uri: Uri,
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpRequest =
    request(uri, GrpcEntityHelpers(e, Source.single(GrpcEntityHelpers.trailer(Status.OK)), eHandler))

  private def request[T](uri: Uri, entity: Source[ChunkStreamPart, NotUsed])(
      implicit marshaller: GrpcProtocolMarshaller): HttpRequest = {
    HttpRequest(
      uri = uri,
      method = HttpMethods.POST,
      headers = immutable.Seq(
        headers.`Message-Encoding`(marshaller.messageEncoding.name),
        headers.`Message-Accept-Encoding`(Codecs.supportedCodecs.map(_.name).mkString(","))),
      entity = HttpEntity.Chunked(marshaller.contentType, entity))
  }

}
