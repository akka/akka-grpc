/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.grpc.{ ProtobufSerializer, Trailers }
import akka.grpc.GrpcProtocol.{ DataFrame, GrpcProtocolWriter, TrailerFrame }
import akka.grpc.scaladsl.{ headers, GrpcExceptionHandler }
import akka.http.scaladsl.model.{ AttributeKeys, HttpEntity, HttpResponse, Trailer }
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

/**
 * Some helpers for creating HTTP entities for use with gRPC.
 *
 * INTERNAL API
 */
@InternalApi // consumed from generated classes so cannot be private
object GrpcResponseHelpers {
  private val TrailerOk = GrpcEntityHelpers.trailer(Status.OK)

  def apply[T](e: Source[T, NotUsed])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers(e, Source.single(TrailerOk))

  def apply[T](e: Source[T, NotUsed], eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers(e, Source.single(TrailerOk), eHandler)

  def responseForSingleElement[T](e: T, eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    try {
      val data = DataFrame(m.serialize(e))
      val strictEntity = HttpEntity.Strict(writer.contentType, writer.encodeFrame(data).data)
      val trailer = Trailer(TrailerOk.trailers)
      HttpResponse(headers = headers.`Message-Encoding`(writer.messageEncoding.name) :: Nil, entity = strictEntity)
        .addAttribute(AttributeKeys.trailer, trailer)
    } catch {
      case NonFatal(ex) =>
        val trailers = GrpcEntityHelpers.handleException(ex, eHandler)
        HttpResponse(headers = headers.`Message-Encoding`(writer.messageEncoding.name) :: Nil).addAttribute(
          AttributeKeys.trailer,
          Trailer(GrpcEntityHelpers.trailer(trailers.status, trailers.metadata).trailers))
    }

  def apply[T](e: Source[T, NotUsed], status: Future[Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers(e, status, GrpcExceptionHandler.defaultMapper _)

  def apply[T](
      e: Source[T, NotUsed],
      status: Future[Status],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse = {
    implicit val ec: ExecutionContext = mat.executionContext
    GrpcResponseHelpers(
      e,
      Source.lazyFuture(() => status.map(GrpcEntityHelpers.trailer(_))).mapMaterializedValue(_ => NotUsed),
      eHandler)
  }

  def apply[T](
      e: Source[T, NotUsed],
      trail: Source[TrailerFrame, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse = {
    response(GrpcEntityHelpers(e, trail, eHandler))
  }

  private def response[T](entity: Source[ChunkStreamPart, NotUsed])(implicit writer: GrpcProtocolWriter) = {
    HttpResponse(
      headers = immutable.Seq(headers.`Message-Encoding`(writer.messageEncoding.name)),
      entity = HttpEntity.Chunked(writer.contentType, entity))
  }

  def status(trailer: Trailers)(implicit writer: GrpcProtocolWriter): HttpResponse =
    response(Source.single(writer.encodeFrame(GrpcEntityHelpers.trailer(trailer.status, trailer.metadata))))
}
