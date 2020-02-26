/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.grpc.Codec
import akka.grpc.GrpcProtocol._
import akka.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart, LastChunk }
import akka.util.ByteString

/**
 * Implementation of the gRPC (`application/grpc+proto`) protocol:
 *
 * Protocol:
 *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC specification
 *  - Trailer frames are encoded to [[LastChunk]], to be rendered into the underlying HTTP/2 transport
 *
 */
object GrpcProtocolNative extends AbstractGrpcProtocol("grpc") {

  override protected def writer(codec: Codec) =
    Grpc.writer(this, codec, encodeFrame(codec, _))

  override protected def reader(codec: Codec): GrpcProtocolReader =
    Grpc.reader(this, codec, decodeFrame)

  @inline
  private def decodeFrame(frameType: Int, data: ByteString) = DataFrame(data)

  @inline
  private def encodeFrame(codec: Codec, frame: Frame): ChunkStreamPart = {
    val compressedFlag = Grpc.fieldType(codec)
    frame match {
      case DataFrame(data)       => Chunk(Grpc.encodeFrameData(compressedFlag, codec.compress(data)))
      case TrailerFrame(headers) => LastChunk(trailer = headers)
    }
  }

}
