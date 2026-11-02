/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }
import java.util.Optional
import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.grpc._
import akka.grpc.internal._
import akka.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import akka.http.javadsl.model.{ HttpEntity, HttpRequest, HttpResponse }
import akka.japi.{ Function => JFunction }
import akka.stream.Materializer
import akka.stream.javadsl.Source
import akka.util.ByteString

import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

object GrpcMarshalling {

  /**
   * Does not touch the request entity when negotiation rejects the request, so the caller can pass the request on.
   * Otherwise the caller must discard the entity. `negotiated(req, f, mat)` does that.
   */
  def negotiated[T](
      req: HttpRequest,
      f: (GrpcProtocolReader, GrpcProtocolWriter) => CompletionStage[T]): Optional[CompletionStage[T]] =
    GrpcProtocol
      .negotiate(req)
      .map {
        case (maybeReader, writer) =>
          maybeReader.map(reader => f(reader, writer)).fold[CompletionStage[T]](failure, identity)
      }
      .fold(Optional.empty[CompletionStage[T]])(Optional.of)

  /**
   * Like `negotiated(req, f)`, but also discards the request entity when negotiation rejects the request.
   */
  def negotiated[T](
      req: HttpRequest,
      f: (GrpcProtocolReader, GrpcProtocolWriter) => CompletionStage[T],
      mat: Materializer): Optional[CompletionStage[T]] =
    GrpcProtocol.negotiate(req) match {
      case Some((Success(reader), writer)) => Optional.of(f(reader, writer))
      case Some((Failure(ex), _)) =>
        req.discardEntityBytes(mat)
        Optional.of(failure(ex))
      case None =>
        req.discardEntityBytes(mat)
        Optional.empty()
    }

  def unmarshal[T](
      data: Source[ByteString, AnyRef],
      u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[T] =
    data.via(reader.dataFrameDecoder).map(u.deserialize).runWith(SingleParameterSink.create[T](), mat)

  def unmarshal[T](
      entity: HttpEntity,
      u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[T] =
    entity match {
      case strict: HttpEntity.Strict =>
        try {
          CompletableFuture.completedFuture(u.deserialize(reader.decodeSingleFrame(strict.getData)))
        } catch {
          case NonFatal(ex) => failure(ex)
        }
      case _ => unmarshal(entity.getDataBytes, u, mat, reader)
    }

  def unmarshalStream[T](
      data: Source[ByteString, AnyRef],
      u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[Source[T, NotUsed]] = {
    CompletableFuture.completedFuture[Source[T, NotUsed]](
      data
        .mapMaterializedValue(_ => NotUsed)
        .via(reader.dataFrameDecoder)
        .map(japiFunction(u.deserialize))
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage)
        .mapMaterializedValue(japiFunction(_ => NotUsed)))
  }
  def unmarshalStream[T](
      entity: HttpEntity,
      u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[Source[T, NotUsed]] =
    unmarshalStream(entity.getDataBytes, u, mat, reader)

  def marshal[T](
      e: T,
      m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      eHandler: JFunction[ActorSystem, JFunction[Throwable, Trailers]] = GrpcExceptionHandler.defaultMapper)
      : HttpResponse =
    GrpcResponseHelpers.responseForSingleElement(e, scalaAnonymousPartialFunction(eHandler))(m, writer, system)

  def marshalStream[T](
      e: Source[T, NotUsed],
      m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      eHandler: JFunction[ActorSystem, JFunction[Throwable, Trailers]] = GrpcExceptionHandler.defaultMapper)
      : HttpResponse =
    GrpcResponseHelpers(e.asScala, scalaAnonymousPartialFunction(eHandler))(m, writer, system)

  private def failure[R](error: Throwable): CompletableFuture[R] = {
    val future: CompletableFuture[R] = new CompletableFuture()
    future.completeExceptionally(error)
    future
  }
}
