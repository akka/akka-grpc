/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.grpc.GrpcProtocol._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart }
import akka.util.ByteString
import io.grpc.{ Status, StatusException }

abstract class GrpcProtocolWebBase(subType: String) extends AbstractGrpcProtocol(subType) {
  protected def postEncode(frame: ByteString): ByteString
  protected def preDecode(frame: ByteString): ByteString

  override protected def writer(codec: Codec): GrpcProtocolWriter =
    AbstractGrpcProtocol.writer(this, codec, frame => encodeFrame(codec, frame))

  override protected def reader(codec: Codec): GrpcProtocolReader =
    AbstractGrpcProtocol.reader(codec, decodeFrame, preDecode)

  @inline
  private def encodeFrame(codec: Codec, frame: Frame): ChunkStreamPart = {
    val dataFrameType = AbstractGrpcProtocol.fieldType(codec)
    val (frameType, data) = frame match {
      case DataFrame(data)       => (dataFrameType, data)
      case TrailerFrame(trailer) => (ByteString(dataFrameType(0) | 0x80), encodeTrailer(trailer))
    }
    val framed = AbstractGrpcProtocol.encodeFrameData(frameType, codec.compress(data))
    Chunk(postEncode(framed))
  }

  @inline
  private final def decodeFrame(frameHeader: Int, data: ByteString): Frame = {
    (frameHeader & 80) match {
      case 0 => DataFrame(data)
      case 1 => TrailerFrame(decodeTrailer(data))
      case f => throw new StatusException(Status.INTERNAL.withDescription(s"Unknown frame type [$f]"))
    }
  }

  @inline
  private final def encodeTrailer(trailer: Seq[HttpHeader]): ByteString =
    ByteString(trailer.mkString("", "\r\n", "\r\n"))

  @inline
  private final def decodeTrailer(data: ByteString): List[HttpHeader] = ???

}

/**
 * Implementation of the gRPC Web protocol.
 *
 * Protocol:
 *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC-web specification.
 *  - Trailer frames are encoded to a [[Chunk]] (containing a marked trailer frame) as per the gRPC-web specification.
 */
object GrpcProtocolWeb extends GrpcProtocolWebBase("grpc-web") {

  override final def preDecode(frame: ByteString): ByteString = frame

  override final def postEncode(frame: ByteString): ByteString = frame

}

/**
 * The `application/grpc-web-text+proto` variant of gRPC.
 *
 * This is the same as `application/grpc-web+proto`, but with each chunk of the frame encoded gRPC data also base64 encoded.
 */
object GrpcProtocolWebText extends GrpcProtocolWebBase("grpc-web-text") {

  override final def postEncode(framed: ByteString): ByteString = framed.encodeBase64

  override final def preDecode(frame: ByteString): ByteString = frame.decodeBase64
}
