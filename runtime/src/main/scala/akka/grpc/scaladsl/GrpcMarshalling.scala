/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc._
import akka.grpc.internal.{ CancellationBarrierGraphStage, GrpcResponseHelpers, MissingParameterException }
import akka.grpc.scaladsl.headers.`Message-Encoding`
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import io.grpc.Status

import scala.concurrent.Future

object GrpcMarshalling {
  def unmarshal[T](req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[T] = {
    val messageEncoding = `Message-Encoding`.findIn(req.headers)
    implicit val ec = mat.executionContext
    req.entity.dataBytes
      .via(Grpc.grpcFramingDecoder(messageEncoding))
      .map(u.deserialize)
      .runWith(Sink.headOption)(mat)
      .flatMap {
        _ match {
          case Some(element) => Future.successful(element)
          case None          => Future.failed(new MissingParameterException())
        }
      }
  }

  def unmarshalStream[T](
      req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[Source[T, NotUsed]] = {
    val messageEncoding = `Message-Encoding`.findIn(req.headers)
    Future.successful(
      req.entity.dataBytes
        .mapMaterializedValue(_ => NotUsed)
        .via(Grpc.grpcFramingDecoder(messageEncoding))
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
      system: ActorSystem): HttpResponse =
    marshalStream(Source.single(e), eHandler)

  def marshalStream[T](
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Status] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      codec: Codec,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers(e, eHandler)
}
