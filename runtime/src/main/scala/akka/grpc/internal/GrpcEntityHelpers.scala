/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.grpc.{ GrpcServiceException, ProtobufSerializer, Trailers }
import akka.grpc.GrpcProtocol.{ DataFrame, Frame, GrpcProtocolWriter, TrailerFrame }
import akka.grpc.scaladsl.{ headers, BytesEntry, Metadata, StringEntry }
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.Source
import io.grpc.Status

/** INTERNAL API */
@InternalApi
object GrpcEntityHelpers {
  def apply[T](
      e: Source[T, NotUsed],
      trail: Source[TrailerFrame, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ActorSystem): Source[ChunkStreamPart, NotUsed] = {
    chunks(e, trail).recover {
      case t =>
        val e = eHandler(system).orElse[Throwable, Trailers] {
          case e: GrpcServiceException => Trailers(e.status, e.metadata)
          case e: Exception            => Trailers(Status.UNKNOWN.withCause(e).withDescription("Stream failed"))
        }(t)
        writer.encodeFrame(trailer(e.status, e.metadata))
    }
  }

  def apply[T](e: T)(implicit m: ProtobufSerializer[T], writer: GrpcProtocolWriter): Source[ChunkStreamPart, NotUsed] =
    chunks(Source.single(e), Source.empty)

  private def chunks[T](e: Source[T, NotUsed], trail: Source[Frame, NotUsed])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter): Source[ChunkStreamPart, NotUsed] =
    e.map { msg => DataFrame(m.serialize(msg)) }.concat(trail).via(writer.frameEncoder)

  def trailer(status: Status): TrailerFrame =
    TrailerFrame(trailers = statusHeaders(status))

  def trailer(status: Status, metadata: Metadata): TrailerFrame =
    TrailerFrame(trailers = statusHeaders(status) ++ metadataHeaders(metadata))

  def statusHeaders(status: Status): List[HttpHeader] =
    List(headers.`Status`(status.getCode.value.toString)) ++ Option(status.getDescription).map(d =>
      headers.`Status-Message`(d))

  def metadataHeaders(metadata: Metadata): List[HttpHeader] =
    metadata.asList.map {
      case (key, StringEntry(value)) => RawHeader(key, value)
      case (key, BytesEntry(value))  => RawHeader(key, MetadataImpl.encodeBinaryHeader(value))
    }
}
