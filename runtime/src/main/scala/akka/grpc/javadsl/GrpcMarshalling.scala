/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc._
import akka.grpc.internal.{ CancellationBarrierGraphStage, GrpcResponseHelpers, MissingParameterException }
import akka.grpc.scaladsl.{ GrpcExceptionHandler => sGrpcExceptionHandler }
import akka.grpc.GrpcProtocol.{ GrpcProtocolMarshaller, GrpcProtocolUnmarshaller }
import akka.http.javadsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import akka.stream.javadsl.{ Sink, Source }
import akka.util.ByteString
import io.grpc.Status

object GrpcMarshalling {
  @Deprecated
  def unmarshal[T](req: HttpRequest, u: ProtobufSerializer[T], mat: Materializer): CompletionStage[T] = {
    GrpcProtocol
      .negotiate(req)
      .map {
        case (maybeUnmarshaller, _) =>
          maybeUnmarshaller
            .map(unmarshaller => unmarshal(req.entity.getDataBytes, u, mat, unmarshaller))
            .fold(failure[T], identity)
      }
      .getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  @Deprecated
  def unmarshalStream[T](
      req: HttpRequest,
      u: ProtobufSerializer[T],
      mat: Materializer): CompletionStage[Source[T, NotUsed]] = {
    GrpcProtocol
      .negotiate(req)
      .map {
        case (maybeUnmarshaller, _) =>
          maybeUnmarshaller
            .map(unmarshaller => unmarshalStream(req.entity.getDataBytes, u, mat, unmarshaller))
            .fold(failure[Source[T, NotUsed]], identity)
      }
      .getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def unmarshal[T](
                    data: Source[ByteString, AnyRef],
                    u: ProtobufSerializer[T],
                    mat: Materializer,
                    unmarshaller: GrpcProtocolUnmarshaller): CompletionStage[T] =
    data.via(unmarshaller.dataFrameDecoder).map(u.deserialize).runWith(Sink.headOption[T], mat).thenCompose[T] { opt =>
      if (opt.isPresent) CompletableFuture.completedFuture(opt.get)
      else failure(new MissingParameterException())
    }

  def unmarshalStream[T](
      data: Source[ByteString, AnyRef],
      u: ProtobufSerializer[T],
      mat: Materializer,
      unmarshaller: GrpcProtocolUnmarshaller): CompletionStage[Source[T, NotUsed]] = {
    CompletableFuture.completedFuture[Source[T, NotUsed]](
      data
        .mapMaterializedValue(_ => NotUsed)
        .via(unmarshaller.dataFrameDecoder)
        .map(japiFunction(u.deserialize))
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage)
        .mapMaterializedValue(japiFunction(_ => NotUsed)))
  }

  @Deprecated
  def marshal[T](e: T, m: ProtobufSerializer[T], mat: Materializer, codec: Codec, system: ActorSystem): HttpResponse =
    marshalStream2(Source.single(e), m, mat, Grpc.newMarshaller(codec), system)

  @Deprecated
  def marshal[T](
      e: T,
      m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem,
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = sGrpcExceptionHandler.defaultMapper): HttpResponse =
    marshalStream2(Source.single(e), m, mat, Grpc.newMarshaller(codec), system, eHandler)

  @Deprecated
  def marshalStream[T](
      e: Source[T, NotUsed],
      m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem,
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = sGrpcExceptionHandler.defaultMapper): HttpResponse =
    marshalStream2(e, m, mat, Grpc.newMarshaller(codec), system, eHandler)

  def marshal2[T](
      e: T,
      m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem,
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = sGrpcExceptionHandler.defaultMapper): HttpResponse =
    marshalStream2(Source.single(e), m, mat, marshaller, system, eHandler)

  def marshalStream2[T](
      e: Source[T, NotUsed],
      m: ProtobufSerializer[T],
      mat: Materializer,
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem,
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = sGrpcExceptionHandler.defaultMapper): HttpResponse =
    GrpcResponseHelpers(e.asScala, eHandler)(m, mat, marshaller, system)

  private def failure[R](error: Throwable): CompletableFuture[R] = {
    val future: CompletableFuture[R] = new CompletableFuture()
    future.completeExceptionally(error)
    future
  }
}
