/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }

import io.grpc.Status
import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.{ HttpEntity => SHttpEntity, HttpResponse => SHttpResponse }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.javadsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import akka.stream.javadsl.{ Sink, Source }
import akka.stream.scaladsl.{ Source => SSource }
import akka.grpc._
import akka.grpc.internal.CancellationBarrierGraphStage
import akka.grpc.scaladsl.headers.`Message-Encoding`

object GrpcMarshalling {
  def unmarshal[T](req: HttpRequest, u: ProtobufSerializer[T], mat: Materializer): CompletionStage[T] = {
    val messageEncoding = `Message-Encoding`.findIn(req.getHeaders)
    (req.entity.getDataBytes via Grpc.grpcFramingDecoder(messageEncoding)).map(u.deserialize).runWith(Sink.head[T], mat)
  }

  def unmarshalStream[T](req: HttpRequest, u: ProtobufSerializer[T], mat: Materializer): CompletionStage[Source[T, NotUsed]] = {
    val messageEncoding = `Message-Encoding`.findIn(req.getHeaders)
    CompletableFuture.completedFuture(
      req.entity.getDataBytes
        .mapMaterializedValue(_ ⇒ NotUsed)
        .via(Grpc.grpcFramingDecoder(messageEncoding))
        .map(u.deserialize)
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage))
  }

  def marshal[T](e: T, m: ProtobufSerializer[T], mat: Materializer, codec: Codec): HttpResponse =
    marshalStream(Source.single(e), m, mat, codec)

  def marshalStream[T](e: Source[T, NotUsed], m: ProtobufSerializer[T], mat: Materializer, codec: Codec): HttpResponse =
    GrpcResponse(e.asScala)(m, mat, Identity)

  def status(status: Status): HttpResponse =
    SHttpResponse(entity = SHttpEntity.Chunked(Grpc.contentType, SSource.single(trailer(status))))

  private def trailer(status: Status): LastChunk =
    LastChunk(trailer = List(RawHeader("grpc-status", status.getCode.value.toString)) ++ Option(status.getDescription).map(RawHeader("grpc-message", _)))

}
