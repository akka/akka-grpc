/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.grpc.GrpcProtocol._
import akka.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart, LastChunk }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpEntity, HttpHeader, HttpProtocols, HttpResponse, StatusCodes, Trailer }
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.collection.immutable

/**
 * Implementation of the gRPC (`application/grpc+proto`) protocol:
 *
 * Protocol:
 *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC specification
 *  - Trailer frames are encoded to [[LastChunk]], to be rendered into the underlying HTTP/2 transport
 */
object GrpcProtocolNative extends AbstractGrpcProtocol("grpc") {

  override protected def writer(codec: Codec) =
    AbstractGrpcProtocol.writer(this, codec, encodeFrame(codec, _), encodeDataToResponse(codec))

  override protected def reader(codec: Codec): GrpcProtocolReader =
    AbstractGrpcProtocol.reader(codec, decodeFrame)

  override def newWriter(codec: Codec): GrpcProtocolWriter = writer(codec)

  override def newReader(codec: Codec): GrpcProtocolReader = reader(codec)

  @inline
  private def decodeFrame(frameType: Int, data: ByteString) = DataFrame(data)

  @inline
  private def encodeFrame(codec: Codec, frame: Frame): ChunkStreamPart =
    frame match {
      case DataFrame(data)       => Chunk(encodeDataToFrameBytes(codec, data))
      case TrailerFrame(headers) => LastChunk(trailer = headers)
    }

  private def encodeDataToResponse(
      codec: Codec)(data: ByteString, headers: immutable.Seq[HttpHeader], trailer: Trailer): HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      headers = headers,
      // We pass the entity as a chunked response. This forces akka-http to send it without a Content-Length header in
      // HTTP/2. While the wire encoding doesn't explicitly forbid a content-length header, it doesn't say there should
      // be one. It appears most, if not all implementations don't send a content length header, so it's probably best
      // not to send one. Clients that are not expecting one, such as envoy's grpc-web filter, can have problems like
      // this: https://github.com/envoyproxy/envoy/issues/40097
      entity = HttpEntity.Chunked(
        contentType,
        Source(
          Seq(
            Chunk(encodeDataToFrameBytes(codec, data)),
            LastChunk(trailer = trailer.headers.map {
              case (header, value) => RawHeader(header, value)
            })))),
      protocol = HttpProtocols.`HTTP/1.1`)

  private def encodeDataToFrameBytes(codec: Codec, data: ByteString): ByteString =
    AbstractGrpcProtocol.encodeFrameData(codec.compress(data), codec.isCompressed, isTrailer = false)
}
