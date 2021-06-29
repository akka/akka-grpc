/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.NotUsed
import akka.annotation.InternalApi
import akka.annotation.InternalStableApi
import akka.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import akka.grpc.internal.{ Codec, Codecs, GrpcProtocolNative, GrpcProtocolWeb, GrpcProtocolWebText }
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.{ ContentType, HttpHeader }
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scala.util.Try

/**
 * A variant of the gRPC protocol - e.g. gRPC and gRPC-Web
 */
trait GrpcProtocol {

  /**
   * INTERNAL API
   *
   * The canonical media type to use for this protocol variant
   */
  @InternalApi
  private[grpc] val contentType: ContentType.Binary

  /**
   * INTERNAL API
   *
   * The set of media types that can identify this protocol variant (e.g. including an implicit +proto)
   */
  @InternalApi
  private[grpc] val mediaTypes: Set[jmodel.MediaType]

  /**
   * INTERNAL API
   *
   * Constructs a protocol writer for writing gRPC protocol frames for this variant
   * @param codec the compression codec to encode data frame bodies with.
   */
  @InternalStableApi
  def newWriter(codec: Codec): GrpcProtocolWriter

  /**
   * INTERNAL API
   *
   * Constructs a protocol reader for reading gRPC protocol frames for this variant.
   * @param codec the compression codec to decode data frame bodies with.
   */
  @InternalStableApi
  def newReader(codec: Codec): GrpcProtocolReader
}

/**
 * INTERNAL API
 *
 * Core definitions for gRPC protocols.
 */
@InternalApi
object GrpcProtocol {

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
      /** The media type produced by this writer */
      contentType: ContentType,
      /** The compression codec to be used for data frame bodies */
      messageEncoding: Codec,
      /** Encodes a frame as a part in a chunk stream. */
      encodeFrame: Frame => ChunkStreamPart,
      /** A shortcut to encode a data frame directly into a ByteString */
      encodeDataToFrameBytes: ByteString => ByteString,
      /** A Flow over a stream of Frame using this frame encoding */
      frameEncoder: Flow[Frame, ChunkStreamPart, NotUsed])

  /**
   * Implements the decoding of the gRPC framing from a physical/transport layer.
   */
  case class GrpcProtocolReader(
      /** The compression codec to be used for data frames */
      messageEncoding: Codec,
      decodeSingleFrame: ByteString => ByteString,
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
  def detect(request: jmodel.HttpRequest): Option[GrpcProtocol] = detect(request.entity.getContentType.mediaType)

  /**
   * Detects which gRPC protocol variant is indicated by a mediatype.
   * @return a [[GrpcProtocol]] matching the request mediatype if known.
   */
  def detect(mediaType: jmodel.MediaType): Option[GrpcProtocol] = mediaType.subType match {
    // FIXME: do we need to check mainType?
    case "grpc" | "grpc+proto"                   => Some(GrpcProtocolNative)
    case "grpc-web" | "grpc-web+proto"           => Some(GrpcProtocolWeb)
    case "grpc-web-text" | "grpc-web-text+proto" => Some(GrpcProtocolWebText)
    case _                                       => None
  }

  /**
   * Calculates the gRPC protocol encoding to use for an interaction with a gRPC client.
   *
   * @param request the client request to respond to.
   * @return the protocol reader for the request, and a protocol writer for the response.
   */
  def negotiate(request: jmodel.HttpRequest): Option[(Try[GrpcProtocolReader], GrpcProtocolWriter)] =
    detect(request).map { variant =>
      (Codecs.detect(request).map(variant.newReader), variant.newWriter(Codecs.negotiate(request)))
    }

}
