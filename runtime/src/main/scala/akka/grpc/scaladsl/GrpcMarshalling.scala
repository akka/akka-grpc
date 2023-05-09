/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import io.grpc.Status

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.grpc._
import akka.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import akka.grpc.internal._
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, Uri }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import java.nio.ByteBuffer

object GrpcMarshalling {

  private object BufferPool {
    private val size = 65536
    private val threadLocalBuffer = new ThreadLocal[ByteBuffer] {
      override def initialValue(): ByteBuffer = ByteBuffer.allocateDirect(size)
    }

    def acquire(length: Int): ByteBuffer =
      if (length <= size) {
        val buf = threadLocalBuffer.get()
        require(buf ne null)
        threadLocalBuffer.set(null)
        buf.clear()
        buf
      } else ByteBuffer.allocate(length) // not direct

    def release(buffer: ByteBuffer) =
      if (buffer.isDirect) {
        val buf = threadLocalBuffer.get()
        require(buf eq null)
        threadLocalBuffer.set(buffer)
      }
  }

  def unmarshal[T](req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[T] = {
    negotiated(
      req,
      (r, _) => {
        implicit val reader: GrpcProtocolReader = r
        unmarshal(req.entity)
      }).getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def unmarshalStream[T](
      req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[Source[T, NotUsed]] = {
    negotiated(
      req,
      (r, _) => {
        implicit val reader: GrpcProtocolReader = r
        unmarshalStream(req.entity)
      }).getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def negotiated[T](req: HttpRequest, f: (GrpcProtocolReader, GrpcProtocolWriter) => Future[T]): Option[Future[T]] =
    GrpcProtocol.negotiate(req).map {
      case (Success(reader), writer) => f(reader, writer)
      case (Failure(ex), _)          => Future.failed(ex)
    }

  def unmarshal[T](data: Source[ByteString, Any])(
      implicit u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): Future[T] = {
    data.via(reader.dataFrameDecoder).map(u.deserialize).runWith(SingleParameterSink())
  }
  def unmarshal[T](
      entity: HttpEntity)(implicit u: ProtobufSerializer[T], mat: Materializer, reader: GrpcProtocolReader): Future[T] =
    entity match {
      case HttpEntity.Strict(_, data) =>
        Future.fromTry(Try {
          val frameData = reader.decodeSingleFrame(data)
          val buf = BufferPool.acquire(frameData.size)
          try {
            frameData.copyToBuffer(buf)
            buf.flip()
            u.deserialize(buf)
          } finally BufferPool.release(buf)
        })
      case _ => unmarshal(entity.dataBytes)
    }

  def unmarshalStream[T](data: Source[ByteString, Any])(
      implicit u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): Future[Source[T, NotUsed]] = {
    Future.successful(
      data
        .mapMaterializedValue(_ => NotUsed)
        .via(reader.dataFrameDecoder)
        .map(u.deserialize)
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage))
  }

  def unmarshalStream[T](entity: HttpEntity)(
      implicit u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): Future[Source[T, NotUsed]] =
    unmarshalStream(entity.dataBytes)

  def marshal[T](
      e: T = Identity,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers.responseForSingleElement(e, eHandler)

  def marshalStream[T](
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse = {
    GrpcResponseHelpers(e, eHandler)
  }

  @InternalApi
  def marshalRequest[T](
      uri: Uri,
      e: T,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpRequest =
    marshalStreamRequest(uri, Source.single(e), eHandler)

  @InternalApi
  def marshalStreamRequest[T](
      uri: Uri,
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpRequest =
    GrpcRequestHelpers(uri, List.empty, e, eHandler)

}
