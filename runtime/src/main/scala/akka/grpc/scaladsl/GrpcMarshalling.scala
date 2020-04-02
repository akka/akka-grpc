/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.grpc._
import akka.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import akka.grpc.internal._
import akka.grpc.ProtobufSerialization.Selector
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.github.ghik.silencer.silent
import io.grpc.Status

import scala.concurrent.Future

object GrpcMarshalling {
  def unmarshal[T](
      req: HttpRequest)(implicit serializers: Selector[ProtobufSerializer[T]], mat: Materializer): Future[T] = {
    negotiated(req, (r, _, format) => {
      unmarshal(req.entity.dataBytes)(serializers(format), mat, r)
    }).getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def unmarshalStream[T](req: HttpRequest)(
      implicit serializers: Selector[ProtobufSerializer[T]],
      mat: Materializer): Future[Source[T, NotUsed]] = {
    negotiated(req, (r, _, format) => {
      unmarshalStream(req.entity.dataBytes)(serializers(format), mat, r)
    }).getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def negotiated[T](
      req: HttpRequest,
      f: (GrpcProtocolReader, GrpcProtocolWriter, ProtobufSerialization) => Future[T]): Option[Future[T]] =
    GrpcProtocol.negotiate(req).map {
      case (maybeReader, writer, format) =>
        maybeReader.map(reader => f(reader, writer, format)).fold(Future.failed, identity)
    }

  def unmarshal[T](data: Source[ByteString, Any])(
      implicit u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): Future[T] = {
    import mat.executionContext
    data.via(reader.dataFrameDecoder).map(u.deserialize).runWith(Sink.headOption).flatMap {
      case Some(element) => Future.successful(element)
      case None          => Future.failed(new MissingParameterException())
    }
  }

  def unmarshalStream[T](data: Source[ByteString, Any])(
      implicit u: ProtobufSerializer[T],
      @silent("never used") mat: Materializer,
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

  def marshal[T](
      e: T = Identity,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ActorSystem): HttpResponse =
    marshalStream(Source.single(e), eHandler)

  def marshalStream[T](
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ActorSystem): HttpResponse = {
    GrpcResponseHelpers(e, eHandler)
  }

  @InternalApi
  def marshalRequest[T](
      uri: Uri,
      e: T,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ActorSystem): HttpRequest =
    marshalStreamRequest(uri, Source.single(e), eHandler)

  @InternalApi
  def marshalStreamRequest[T](
      uri: Uri,
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ActorSystem): HttpRequest =
    GrpcRequestHelpers(uri, e, eHandler)

}
