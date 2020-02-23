/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import io.grpc.Status
import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.grpc.{ Codec, Grpc, GrpcServiceException, ProtobufSerializer }
import akka.grpc.scaladsl.{ headers, GrpcExceptionHandler }
import akka.grpc.GrpcProtocol.{ DataFrame, Frame, GrpcProtocolMarshaller, TrailerFrame }
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpEntity.{ ChunkStreamPart, LastChunk }
import akka.http.scaladsl.model.HttpHeader
import akka.stream.Materializer
import akka.stream.scaladsl.Source

/** INTERNAL API */
@InternalApi
object GrpcEntityHelpers {
  def apply[T](
      e: Source[T, NotUsed],
      trail: Source[TrailerFrame, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): Source[ChunkStreamPart, NotUsed] = {
    chunks(e, trail).recover {
      case t =>
        val status = eHandler(system).orElse[Throwable, Status] {
          case e: GrpcServiceException => e.status
          case e: Exception            => Status.UNKNOWN.withCause(e).withDescription("Stream failed")
        }(t)
        marshaller.encodeFrame(trailer(status))
    }
  }

  def apply[T](e: T)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): Source[ChunkStreamPart, NotUsed] =
    chunks(Source.single(e), Source.empty)

  private def chunks[T](e: Source[T, NotUsed], trail: Source[Frame, NotUsed])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): Source[ChunkStreamPart, NotUsed] =
    e.map { msg => DataFrame(m.serialize(msg)) }.concat(trail).via(marshaller.frameEncoder)

  def trailer(status: Status): TrailerFrame =
    TrailerFrame(trailers = statusHeaders(status))

  def statusHeaders(status: Status): List[HttpHeader] =
    List(headers.`Status`(status.getCode.value.toString)) ++ Option(status.getDescription).map(d =>
      headers.`Status-Message`(d))
}
