/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.grpc.GrpcProtocol._
import akka.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart, LastChunk }
import akka.util.ByteString
import com.github.ghik.silencer.silent

/**
 * Implementation of the gRPC (`application/grpc+proto`) protocol:
 *
 * Protocol:
 *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC specification
 *  - Trailer frames are encoded to [[LastChunk]], to be rendered into the underlying HTTP/2 transport
 */
object GrpcProtocolNative extends AbstractGrpcProtocol("grpc") {

  override protected def writer(codec: Codec) =
    AbstractGrpcProtocol.writer(this, codec, encodeFrame(codec, _))

  override protected def reader(codec: Codec): GrpcProtocolReader =
    AbstractGrpcProtocol.reader(codec, decodeFrame)

  @inline
  private def decodeFrame(@silent("never used") frameType: Int, data: ByteString) = DataFrame(data)

  @inline
  private def encodeFrame(codec: Codec, frame: Frame): ChunkStreamPart = {
    val compressedFlag = AbstractGrpcProtocol.fieldType(codec)
    frame match {
      case DataFrame(data)       => Chunk(AbstractGrpcProtocol.encodeFrameData(compressedFlag, codec.compress(data)))
      case TrailerFrame(headers) => LastChunk(trailer = headers)
    }
  }

}
