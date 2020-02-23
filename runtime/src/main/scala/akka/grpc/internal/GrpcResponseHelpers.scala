/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.grpc.{ GrpcServiceException, ProtobufSerializer }
import akka.grpc.GrpcProtocol.{ DataFrame, GrpcProtocolMarshaller, TrailerFrame }
import akka.grpc.scaladsl.{ headers, GrpcExceptionHandler }
import akka.http.scaladsl.model.{ HttpEntity, HttpHeader, HttpMessage, HttpResponse, ResponseEntity }
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

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
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers(e, Source.single(GrpcEntityHelpers.trailer(Status.OK)))

  def apply[T](e: Source[T, NotUsed], eHandler: ActorSystem => PartialFunction[Throwable, Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers(e, Source.single(GrpcEntityHelpers.trailer(Status.OK)), eHandler)

  def apply[T](e: Source[T, NotUsed], status: Future[Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers(e, status, GrpcExceptionHandler.defaultMapper _)

  def apply[T](
      e: Source[T, NotUsed],
      status: Future[Status],
      eHandler: ActorSystem => PartialFunction[Throwable, Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpResponse = {
    implicit val ec: ExecutionContext = mat.executionContext
    GrpcResponseHelpers(
      e,
      Source.lazilyAsync(() => status.map(GrpcEntityHelpers.trailer)).mapMaterializedValue(_ => NotUsed),
      eHandler)
  }

  def apply[T](
      e: Source[T, NotUsed],
      trail: Source[TrailerFrame, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpResponse = {
    response(GrpcEntityHelpers(e, trail, eHandler))
  }

  private def response[T](entity: Source[ChunkStreamPart, NotUsed])(implicit marshaller: GrpcProtocolMarshaller) = {
    HttpResponse(
      headers = immutable.Seq(headers.`Message-Encoding`(marshaller.messageEncoding.name)),
      entity = HttpEntity.Chunked(marshaller.contentType, entity))
  }

  def status(status: Status)(implicit marshaller: GrpcProtocolMarshaller): HttpResponse =
    response(Source.single(marshaller.encodeFrame(GrpcEntityHelpers.trailer(status))))

}
