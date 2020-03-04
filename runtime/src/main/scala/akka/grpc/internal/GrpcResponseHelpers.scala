/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.grpc.scaladsl.{ headers, GrpcExceptionHandler }
import akka.grpc.{ Codec, Grpc, ProtobufSerializer, Trailers }
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.{ HttpEntity, HttpHeader, HttpResponse }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status
import scala.collection.immutable
import scala.concurrent.Future

/**
 * Some helpers for creating HTTP entities for use with gRPC.
 *
 * INTERNAL API
 */
@InternalApi // consumed from generated classes so cannot be private
object GrpcResponseHelpers {
  def apply[T](e: Source[T, NotUsed])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers(e, Source.single(GrpcEntityHelpers.trailer(Status.OK)))

  def apply[T](e: Source[T, NotUsed], eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers(e, Source.single(GrpcEntityHelpers.trailer(Status.OK)), eHandler)

  def apply[T](e: Source[T, NotUsed], status: Future[Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers(e, status, GrpcExceptionHandler.defaultMapper _)

  def apply[T](
      e: Source[T, NotUsed],
      status: Future[Status],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse = {
    implicit val ec = mat.executionContext
    GrpcResponseHelpers(
      e,
      Source.lazilyAsync(() => status.map(GrpcEntityHelpers.trailer(_))).mapMaterializedValue(_ => NotUsed),
      eHandler)
  }

  def apply[T](
      e: Source[T, NotUsed],
      trail: Source[HttpEntity.LastChunk, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse = {

    HttpResponse(
      headers = immutable.Seq(headers.`Message-Encoding`(codec.name)),
      entity = GrpcEntityHelpers(e, trail, eHandler))
  }

  def status(e: Trailers): HttpResponse =
    HttpResponse(entity =
      HttpEntity.Chunked(Grpc.contentType, Source.single(GrpcEntityHelpers.trailer(e.status, e.metadata))))
}
