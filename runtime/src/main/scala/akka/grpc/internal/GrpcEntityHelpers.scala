/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.Base64

import io.grpc.Status
import akka.{ grpc, NotUsed }
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.grpc.{
  BytesEntry,
  Codec,
  Grpc,
  GrpcErrorResponse,
  GrpcServiceException,
  MetadataEntry,
  ProtobufSerializer,
  StringEntry
}
import akka.grpc.scaladsl.headers
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import akka.stream.scaladsl.Source

/** INTERNAL API */
@InternalApi
object GrpcEntityHelpers {
  def apply[T](
      e: Source[T, NotUsed],
      trail: Source[HttpEntity.LastChunk, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, GrpcErrorResponse])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpEntity.Chunked = {
    HttpEntity.Chunked(Grpc.contentType, chunks(e, trail).recover {
      case t =>
        val e = eHandler(system).orElse[Throwable, GrpcErrorResponse] {
          case e: GrpcServiceException => grpc.GrpcErrorResponse(e.status, e.metadata)
          case e: Exception            => grpc.GrpcErrorResponse(Status.UNKNOWN.withCause(e).withDescription("Stream failed"))
        }(t)
        trailer(e.status, e.metadata)
    })
  }

  def apply[T](e: T)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpEntity.Chunked =
    HttpEntity.Chunked(Grpc.contentType, chunks(Source.single(e), Source.empty))

  private def chunks[T](e: Source[T, NotUsed], trail: Source[HttpEntity.LastChunk, NotUsed])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem) =
    e.map(m.serialize).via(Grpc.grpcFramingEncoder(codec)).map(bytes => HttpEntity.Chunk(bytes)).concat(trail)

  def trailer(status: Status, headers: List[HttpHeader] = Nil): LastChunk =
    LastChunk(trailer = statusHeaders(status) ++ headers)

  def trailer(status: Status, metadata: Map[String, MetadataEntry]): LastChunk =
    LastChunk(trailer = statusHeaders(status) ++ metadataHeaders(metadata))

  def statusHeaders(status: Status): List[HttpHeader] =
    List(headers.`Status`(status.getCode.value.toString)) ++ Option(status.getDescription).map(d =>
      headers.`Status-Message`(d))

  def metadataHeaders(metadata: Map[String, MetadataEntry]): List[HttpHeader] =
    metadata.map {
      case (key, StringEntry(value)) => RawHeader(key, value)
      case (key, BytesEntry(value))  => RawHeader(key, new String(Base64.getEncoder.encode(value.toByteBuffer).array))
    }.toList
}
