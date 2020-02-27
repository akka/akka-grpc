/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.javadsl.model.{ HttpRequest, HttpResponse }
import akka.japi.Function
import akka.stream.Materializer
import akka.stream.javadsl.{ Sink, Source }
import akka.grpc._
import akka.grpc.internal.{ CancellationBarrierGraphStage, GrpcResponseHelpers, MissingParameterException }
import akka.grpc.scaladsl.headers.`Message-Encoding`

object GrpcMarshalling {
  def unmarshal[T](req: HttpRequest, u: ProtobufSerializer[T], mat: Materializer): CompletionStage[T] = {
    val messageEncoding = `Message-Encoding`.findIn(req.getHeaders)
    req.entity.getDataBytes
      .via(Grpc.grpcFramingDecoder(messageEncoding))
      .map(japiFunction(u.deserialize))
      .runWith(Sink.headOption[T], mat)
      .thenCompose { opt =>
        if (opt.isPresent) CompletableFuture.completedFuture(opt.get)
        else failure(new MissingParameterException())
      }
  }

  def unmarshalStream[T](
      req: HttpRequest,
      u: ProtobufSerializer[T],
      mat: Materializer): CompletionStage[Source[T, NotUsed]] = {
    val messageEncoding = `Message-Encoding`.findIn(req.getHeaders)
    CompletableFuture.completedFuture(
      req.entity.getDataBytes
        .via(Grpc.grpcFramingDecoder(messageEncoding))
        .map(japiFunction(u.deserialize))
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage)
        .mapMaterializedValue(japiFunction(_ => NotUsed)))
  }

  def marshal[T](e: T, m: ProtobufSerializer[T], mat: Materializer, codec: Codec, system: ActorSystem): HttpResponse =
    marshalStream(Source.single(e), m, mat, codec, system)

  def marshal[T](
      e: T,
      m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem,
      eHandler: Function[ActorSystem, Function[Throwable, GrpcErrorResponse]] = GrpcExceptionHandler.defaultMapper)
      : HttpResponse =
    marshalStream(Source.single(e), m, mat, codec, system, eHandler)

  def marshalStream[T](
      e: Source[T, NotUsed],
      m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem,
      eHandler: Function[ActorSystem, Function[Throwable, GrpcErrorResponse]] = GrpcExceptionHandler.defaultMapper)
      : HttpResponse =
    GrpcResponseHelpers(e.asScala, scalaAnonymousPartialFunction(eHandler))(m, mat, Identity, system)

  def status(e: GrpcErrorResponse): HttpResponse =
    GrpcResponseHelpers.status(e)

  private def failure[R](error: Throwable): CompletableFuture[R] = {
    val future: java.util.concurrent.CompletableFuture[R] = new CompletableFuture();
    future.completeExceptionally(error);
    future
  }
}
