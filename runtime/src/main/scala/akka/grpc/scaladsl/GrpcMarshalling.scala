/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc._
import akka.grpc.GrpcProtocol.{ GrpcProtocolMarshaller, GrpcProtocolUnmarshaller }
import akka.grpc.internal.{ CancellationBarrierGraphStage, GrpcResponseHelpers, MissingParameterException }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import io.grpc.Status

import scala.concurrent.Future

object GrpcMarshalling {
  def unmarshal[T](req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[T] = {
    GrpcProtocol
      .negotiate(req)
      .map {
        case (maybeUnmarshaller, _) =>
          maybeUnmarshaller.map(implicit unmarshaller => unmarshal(req.entity.dataBytes)).fold(Future.failed, identity)
      }
      .getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def unmarshalStream[T](
      req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[Source[T, NotUsed]] = {
    GrpcProtocol
      .negotiate(req)
      .map {
        case (maybeUnmarshaller, _) =>
          maybeUnmarshaller
            .map(implicit unmarshaller => unmarshalStream(req.entity.dataBytes))
            .fold(Future.failed, identity)
      }
      .getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
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
