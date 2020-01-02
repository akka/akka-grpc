/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.grpc.scaladsl.{ headers, GrpcExceptionHandler }
import akka.grpc.{ Codec, Grpc, GrpcServiceException, ProtobufSerializer }
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.{ HttpEntity, HttpHeader, HttpResponse }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status

import scala.collection.immutable
import scala.concurrent.Future

/**
 * Some helpers for creating HTTP responses for use with gRPC
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
    GrpcResponseHelpers(e, Source.single(trailer(Status.OK)))

  def apply[T](e: Source[T, NotUsed], eHandler: ActorSystem => PartialFunction[Throwable, Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers(e, Source.single(trailer(Status.OK)), eHandler)

  def apply[T](e: Source[T, NotUsed], status: Future[Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers(e, status, GrpcExceptionHandler.defaultMapper _)

  def apply[T](
      e: Source[T, NotUsed],
      status: Future[Status],
      eHandler: ActorSystem => PartialFunction[Throwable, Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse = {
    implicit val ec = mat.executionContext
    GrpcResponseHelpers(
      e,
      Source.lazilyAsync(() => status.map(trailer(_))).mapMaterializedValue(_ => NotUsed),
      eHandler)
  }

  def apply[T](
      e: Source[T, NotUsed],
      trail: Source[HttpEntity.LastChunk, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse = {
    val outChunks = e
      .map(m.serialize)
      .via(Grpc.grpcFramingEncoder(codec))
      .map(bytes => HttpEntity.Chunk(bytes))
      .concat(trail)
      .recover {
        case t =>
          val status = eHandler(system).orElse[Throwable, Status] {
            case e: GrpcServiceException => e.status
            case e: Exception            => Status.UNKNOWN.withCause(e).withDescription("Stream failed")
          }(t)
          trailer(status)
      }

    HttpResponse(
      headers = immutable.Seq(headers.`Message-Encoding`(codec.name)),
      entity = HttpEntity.Chunked(Grpc.contentType, outChunks))
  }

  def status(status: Status): HttpResponse =
    HttpResponse(entity = HttpEntity.Chunked(Grpc.contentType, Source.single(trailer(status))))

  def trailer(status: Status): LastChunk =
    LastChunk(trailer = statusHeaders(status))

  def statusHeaders(status: Status): List[HttpHeader] =
    List(headers.`Status`(status.getCode.value.toString)) ++ Option(status.getDescription).map(d =>
      headers.`Status-Message`(d))
}
