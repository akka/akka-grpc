/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.NotUsed
import akka.grpc._
import akka.grpc.internal.{ CancellationBarrierGraphStage, GrpcResponseHelpers }
import akka.grpc.scaladsl.headers.`Message-Encoding`
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

import scala.concurrent.Future

object GrpcMarshalling {
  def unmarshal[T](req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[T] = {
    val messageEncoding = `Message-Encoding`.findIn(req.headers)
    req.entity.dataBytes
      .via(Grpc.grpcFramingDecoder(messageEncoding))
      .map(u.deserialize)
      .runWith(Sink.head)(mat)
  }

  def unmarshalStream[T](req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[Source[T, NotUsed]] = {
    val messageEncoding = `Message-Encoding`.findIn(req.headers)
    Future.successful(
      req.entity.dataBytes
        .mapMaterializedValue(_ ⇒ NotUsed)
        .via(Grpc.grpcFramingDecoder(messageEncoding))
        .map(u.deserialize)
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage))
  }

  def marshal[T](e: T = Identity)(implicit m: ProtobufSerializer[T], mat: Materializer, codec: Codec): HttpResponse =
    marshalStream(Source.single(e))

  def marshalStream[T](e: Source[T, NotUsed])(implicit m: ProtobufSerializer[T], mat: Materializer, codec: Codec): HttpResponse =
    GrpcResponseHelpers(e)

}
