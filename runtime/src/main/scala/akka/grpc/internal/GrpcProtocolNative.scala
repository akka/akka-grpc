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
  private val identityReader = AbstractGrpcProtocol.reader(Identity, decodeFrame)
  private val identityWriter =
    AbstractGrpcProtocol.writer(this, Identity, encodeFrame(Identity, _), encodeDataToFrameBytes(Identity, _))

  private val gzipReader = AbstractGrpcProtocol.reader(Gzip, decodeFrame)
  private val gzipWriter =
    AbstractGrpcProtocol.writer(this, Gzip, encodeFrame(Gzip, _), encodeDataToFrameBytes(Gzip, _))

  override protected def writer(codec: Codec) = codec match {
    case Identity => identityWriter
    case Gzip     => gzipWriter
  }

  override protected def reader(codec: Codec): GrpcProtocolReader = codec match {
    case Identity => identityReader
    case Gzip     => gzipReader
  }
  override def newWriter(codec: Codec): GrpcProtocolWriter = writer(codec)
  override def newReader(codec: Codec): GrpcProtocolReader = reader(codec)

  @inline
  private def decodeFrame(@silent("never used") frameType: Int, data: ByteString) = DataFrame(data)

  @inline
  private def encodeFrame(codec: Codec, frame: Frame): ChunkStreamPart =
    frame match {
      case DataFrame(data)       => Chunk(encodeDataToFrameBytes(codec, data))
      case TrailerFrame(headers) => LastChunk(trailer = headers)
    }
  private def encodeDataToFrameBytes(codec: Codec, data: ByteString): ByteString =
    AbstractGrpcProtocol.encodeFrameDataCheap(codec != Identity, codec.compress(data))
}
