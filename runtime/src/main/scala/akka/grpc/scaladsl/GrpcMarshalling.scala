/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.grpc._
import akka.grpc.GrpcProtocol.{ GrpcProtocolMarshaller, GrpcProtocolUnmarshaller }
import akka.grpc.internal.{
  CancellationBarrierGraphStage,
  GrpcRequestHelpers,
  GrpcResponseHelpers,
  MissingParameterException
}
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import io.grpc.Status

import scala.concurrent.Future

object GrpcMarshalling {
  def unmarshal[T](req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[T] = {
    negotiated(req, (um, _) => {
      implicit val unmarshaller = um
      unmarshal(req.entity.dataBytes)
    }).getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def unmarshalStream[T](
      req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[Source[T, NotUsed]] = {
    negotiated(req, (um, _) => {
      implicit val unmarshaller = um
      unmarshalStream(req.entity.dataBytes)
    }).getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def negotiated[T](
      req: HttpRequest,
      f: (GrpcProtocolUnmarshaller, GrpcProtocolMarshaller) => Future[T]): Option[Future[T]] =
    GrpcProtocol.negotiate(req).map {
      case (maybeUnmarshaller, marshaller) =>
        maybeUnmarshaller.map(unmarshaller => f(unmarshaller, marshaller)).fold(Future.failed, identity)
    }

  def unmarshal[T](data: Source[ByteString, Any])(
      implicit u: ProtobufSerializer[T],
      mat: Materializer,
      unmarshaller: GrpcProtocolUnmarshaller): Future[T] = {
    import mat.executionContext
    data.via(unmarshaller.dataFrameDecoder).map(u.deserialize).runWith(Sink.headOption).flatMap {
      case Some(element) => Future.successful(element)
      case None          => Future.failed(new MissingParameterException())
    }
  }

  def unmarshalStream[T](data: Source[ByteString, Any])(
      implicit u: ProtobufSerializer[T],
      mat: Materializer,
      unmarshaller: GrpcProtocolUnmarshaller): Future[Source[T, NotUsed]] = {
    Future.successful(
      data
        .mapMaterializedValue(_ => NotUsed)
        .via(unmarshaller.dataFrameDecoder)
        .map(u.deserialize)
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage))
  }

  @deprecated("To be removed", "grpc-web")
  def marshal[T](
      e: T = Identity,
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse = {
    implicit val grpc: GrpcProtocolMarshaller = Grpc.newMarshaller(codec)
    marshalStream2(Source.single(e), eHandler)
  }

  @deprecated("To be removed", "grpc-web")
  def marshalStream[T](
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse = {
    implicit val grpc: GrpcProtocolMarshaller = Grpc.newMarshaller(codec)
    marshalStream2(e, eHandler)
  }

  @InternalApi
  def marshalRequest[T](
      uri: Uri,
      e: T,
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpRequest =
    marshalStreamRequest(uri, Source.single(e), eHandler)

  @InternalApi
  def marshalStreamRequest[T](
      uri: Uri,
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpRequest =
    GrpcRequestHelpers(uri, e, eHandler)

  def marshal2[T](
      e: T = Identity,
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpResponse =
    marshalStream2(Source.single(e), eHandler)

  def marshalStream2[T](
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpResponse = {
    GrpcResponseHelpers(e, eHandler)
  }
}
