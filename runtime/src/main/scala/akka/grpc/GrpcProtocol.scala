/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.NotUsed
import akka.annotation.InternalApi
import akka.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import akka.grpc.internal._
import akka.grpc.ProtobufSerialization.Protobuf
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.{ ContentType, HttpHeader, MediaType }
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scala.util.Try

/**
 * A variant of the gRPC protocol - e.g. gRPC and gRPC-Web
 */
trait GrpcProtocol {

  /** The canonical media subType to use for this protocol variant, without an explicit serialization format (e.g. no +proto or +json) */
  val subType: String

  def contentType(implicit format: ProtobufSerialization): ContentType.Binary =
    MediaType.applicationBinary(s"${subType}+${format.name}", MediaType.Compressible)

  /**
   * INTERNAL API
   *
   * Constructs a protocol writer for writing gRPC protocol frames for this variant
   * @param codec the compression codec to encode data frame bodies with.
   */
  @InternalApi
  def newWriter(codec: Codec): GrpcProtocolWriter

  /**
   * INTERNAL API
   *
   * Constructs a protocol reader for reading gRPC protocol frames for this variant.
   * @param codec the compression codec to decode data frame bodies with.
   */
  @InternalApi
  def newReader(codec: Codec): GrpcProtocolReader
}

/**
 * Core definitions for gRPC protocols.
 */
@InternalApi
object GrpcProtocol {

  private[grpc] val protocols: Seq[GrpcProtocol] = Seq(GrpcProtocolNative, GrpcProtocolWeb, GrpcProtocolWebText)
  private[grpc] val formats: Seq[ProtobufSerialization] =
    Seq(ProtobufSerialization.Protobuf, ProtobufSerialization.Json)

  /** A frame in a logical gRPC protocol stream */
  sealed trait Frame

  /** A data (or message) frame in a gRPC protocol stream. */
  case class DataFrame(data: ByteString) extends Frame

  /** A trailer (status headers) frame in a gRPC protocol stream */
  case class TrailerFrame(trailers: List[HttpHeader]) extends Frame

  /**
   * Implements the encoding of a stream of gRPC Frames into a physical/transport layer.
   *
   * This maps the logical gRPC frames into a stream of chunks that can be handled by the HTTP/2 or HTTP/1.1 transport layer.
   */
  case class GrpcProtocolWriter(
      /** The protocol this writer encodes for */
      protocol: GrpcProtocol,
      /** The compression codec to be used for data frame bodies */
      messageEncoding: Codec,
      /** Encodes a frame as a part in a chunk stream. */
      encodeFrame: Frame => ChunkStreamPart,
      /** A Flow over a stream of Frame using this frame encoding */
      frameEncoder: Flow[Frame, ChunkStreamPart, NotUsed])

  /**
   * Implements the decoding of the gRPC framing from a physical/transport layer.
   */
  case class GrpcProtocolReader(
      /** The compression codec to be used for data frames */
      messageEncoding: Codec,
      /** A Flow of Frames over a stream of messages encoded in gRPC framing. */
      frameDecoder: Flow[ByteString, Frame, NotUsed]) {

    /**
     * A Flow of Frames over a stream of messages encoded in gRPC framing that only
     * expects data frames, and produces the body of each data frame.
     * This flow will throw IllegalStateException if anything other than a data frame is encountered.
     */
    val dataFrameDecoder: Flow[ByteString, ByteString, NotUsed] = frameDecoder.map {
      case DataFrame(data) => data
      case _               => throw new IllegalStateException("Expected only Data frames in stream")
    }
  }

  /**
   * Detects which gRPC protocol variant is indicated in a request.
   * @return a [[GrpcProtocol]] matching the request mediatype if specified and known.
   */
  def detect(request: jmodel.HttpRequest): Option[(GrpcProtocol, ProtobufSerialization)] =
    detect(request.entity.getContentType.mediaType)

  /**
   * Detects which gRPC protocol variant and message serialization is indicated by a mediatype.
   * e.g. `application/grpc`, `application/grpc-web+proto`, `application/grpc+json`.
   * @return The [[GrpcProtocol]] and [[ProtobufSerialization]] specified by the mediatype,
   *         or None if the protocol or serialization format is unknown.
   */
  def detect(mediaType: jmodel.MediaType): Option[(GrpcProtocol, ProtobufSerialization)] = {
    if (!mediaType.isApplication) None
    else {
      val mSubType = mediaType.subType
      protocols
        .find { p =>
          val baseSubType = p.subType
          mSubType.startsWith(baseSubType) && ((baseSubType.length == mSubType.length) || (mSubType(baseSubType.length) == '+'))
        }
        .flatMap { p =>
          if (mSubType.length == p.subType.length) Some((p, Protobuf))
          else {
            val formatSpec = mSubType.substring(p.subType.length + 1)
            formats.find(_.name == formatSpec).map((p, _))
          }
        }
    }
  }

  /**
   * Calculates the gRPC protocol encoding to use for an interaction with a gRPC client.
   * @see detect(jmodel.MediaType)
   * @param request the client request to respond to.
   * @return the protocol reader for the request, a protocol writer for the response,
   *         and the protobuf serialization format to use.
   */
  def negotiate(
      request: jmodel.HttpRequest): Option[(Try[GrpcProtocolReader], GrpcProtocolWriter, ProtobufSerialization)] =
    detect(request).map {
      case (proto, format) =>
        (Codecs.detect(request).map(proto.newReader), proto.newWriter(Codecs.negotiate(request)), format)
    }

}
