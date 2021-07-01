/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.grpc.{ ProtobufSerializer, Trailers }
import akka.grpc.GrpcProtocol.{ GrpcProtocolWriter, TrailerFrame }
import akka.grpc.scaladsl.{ headers, GrpcExceptionHandler }
import akka.http.scaladsl.model.{
  AttributeKey,
  AttributeKeys,
  HttpEntity,
  HttpHeader,
  HttpProtocols,
  HttpResponse,
  ResponseEntity,
  StatusCodes,
  Trailer
}
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
  private val TrailerOkAttribute = Trailer(TrailerOk.trailers)

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
      val strictEntity = HttpEntity.Strict(writer.contentType, writer.encodeDataToFrameBytes((m.serialize(e))))
      responseWithTrailers(
        headers.`Message-Encoding`(writer.messageEncoding.name) :: Nil,
        strictEntity,
        TrailerOkAttribute)
    } catch {
      case NonFatal(ex) =>
        val trailers = GrpcEntityHelpers.handleException(ex, eHandler)
        responseWithTrailers(
          headers.`Message-Encoding`(writer.messageEncoding.name) :: Nil,
          HttpEntity.Empty,
          Trailer(GrpcEntityHelpers.trailer(trailers.status, trailers.metadata).trailers))
    }

  private def responseWithTrailers(
      headers: immutable.Seq[HttpHeader],
      entity: ResponseEntity,
      trailer: Trailer): HttpResponse =
    new HttpResponse(
      status = StatusCodes.OK,
      headers = headers,
      entity = entity,
      protocol = HttpProtocols.`HTTP/2.0`,
      attributes = Map.empty[AttributeKey[_], Any].updated(AttributeKeys.trailer, trailer))

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
