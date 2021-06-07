/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.grpc.{ ProtobufSerializer, Trailers }
import akka.grpc.GrpcProtocol.GrpcProtocolWriter
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.stream.scaladsl.Source
import akka.NotUsed
import akka.annotation.InternalApi
import akka.grpc.scaladsl.GrpcExceptionHandler
import akka.grpc.scaladsl.headers._
import akka.http.scaladsl.model
import akka.http.scaladsl.model.{ HttpEntity, HttpHeader, HttpMethods, HttpRequest, TransferEncodings, Uri }

import scala.collection.immutable

@InternalApi
object GrpcRequestHelpers {

  def apply[T](
      uri: Uri,
      headers: immutable.Seq[HttpHeader],
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpRequest =
    request(uri, headers, GrpcEntityHelpers(e, trail = Source.empty, eHandler))

  private def request[T](uri: Uri, headers: immutable.Seq[HttpHeader], entity: Source[ChunkStreamPart, NotUsed])(
      implicit writer: GrpcProtocolWriter): HttpRequest = {
    HttpRequest(
      uri = uri,
      method = HttpMethods.POST,
      // FIXME how can client exclude gzip?
      headers = immutable.Seq(
        `Message-Encoding`(writer.messageEncoding.name),
        `Message-Accept-Encoding`(Codecs.supportedCodecs.map(_.name).mkString(",")),
        model.headers.TE(TransferEncodings.trailers)) ++ headers,
      entity = HttpEntity.Chunked(writer.contentType, entity))
  }

}
