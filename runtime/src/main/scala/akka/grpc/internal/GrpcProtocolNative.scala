/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.grpc.GrpcProtocol._
import akka.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart, LastChunk }
import akka.util.ByteString
import scala.annotation.nowarn

/**
 * Implementation of the gRPC (`application/grpc+proto`) protocol:
 *
 * Protocol:
 *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC specification
 *  - Trailer frames are encoded to [[LastChunk]], to be rendered into the underlying HTTP/2 transport
 */
object GrpcProtocolNative extends AbstractGrpcProtocol("grpc") {

  override protected def writer(codec: Codec) =
    AbstractGrpcProtocol.writer(this, codec, encodeFrame(codec, _), encodeDataToFrameBytes(codec, _))

  override protected def reader(codec: Codec): GrpcProtocolReader =
    AbstractGrpcProtocol.reader(codec, decodeFrame)

  @inline
  private def decodeFrame(@nowarn("cat=unused-params") frameType: Int, data: ByteString) = DataFrame(data)

  @inline
  private def encodeFrame(codec: Codec, frame: Frame): ChunkStreamPart =
    frame match {
      case DataFrame(data) =>
        Chunk(AbstractGrpcProtocol.encodeFrameData(codec.compress(data), codec.isCompressed, isTrailer = false))
      case TrailerFrame(headers) => LastChunk(trailer = headers)
    }
  private def encodeDataToFrameBytes(codec: Codec, data: ByteString): ByteString =
    AbstractGrpcProtocol.encodeFrameData(codec.compress(data), codec.isCompressed, isTrailer = false)
}
