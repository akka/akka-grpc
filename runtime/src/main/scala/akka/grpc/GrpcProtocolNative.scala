/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.grpc.GrpcProtocol._
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart, LastChunk }
import akka.stream.Attributes
import akka.stream.impl.io.ByteStringParser
import akka.stream.impl.io.ByteStringParser.{ ByteReader, ParseResult, ParseStep }
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStageLogic
import akka.util.ByteString
import io.grpc.{ Status, StatusException }

/**
 * Implementation of the gRPC protocol.
 */
object GrpcProtocolNative extends GrpcProtocol {

  /** The `application/grpc+proto` content type */
  override val contentType = MediaType.applicationBinary("grpc+proto", MediaType.Compressible).toContentType
  private val shortMediaType = MediaType.applicationBinary("grpc", MediaType.Compressible)

  private[grpc] def adjustCompressibility(contentType: ContentType.Binary, codec: Codec) =
    contentType.mediaType
      .withComp(codec match {
        case Identity => MediaType.Compressible
        case _        => MediaType.NotCompressible
      })
      .toContentType

  override val mediaTypes: Set[jmodel.MediaType] = Set(contentType.mediaType, shortMediaType)

  /**
   * Obtains a writer for the `application/grpc+proto` protocol:
   *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC specification
   *  - Trailer frames are encoded to [[LastChunk]], to be rendered into the underlying HTTP/2 transport
   *
   * @param codec the compression codec to apply to data frame contents.
   */
  override def newWriter(codec: Codec): GrpcProtocolWriter = codec match {
    case Identity => grpcIdentityWriter
    case Gzip     => grpcGzipWriter
    case _        => writer(codec)
  }

  /**
   * Obtains an reader for the `application/grpc+proto` protocol.
   *
   * @param codec the codec to use for compressed frames.
   */
  override def newReader(codec: Codec): GrpcProtocolReader = codec match {
    case Identity => grpcIdentityReader
    case Gzip     => grpcGzipReader
    case _        => reader(codec)
  }

  private val grpcIdentityWriter = writer(Identity)
  private val grpcGzipWriter = writer(Gzip)
  private val grpcIdentityReader = reader(Identity)
  private val grpcGzipReader = reader(Gzip)

  private def writer(codec: Codec) =
    GrpcProtocolWriter(
      adjustCompressibility(GrpcProtocolNative.contentType, codec),
      codec,
      GrpcProtocolNative.encodeFrame(codec, _),
      Flow[Frame].map(GrpcProtocolNative.encodeFrame(codec, _)))

  private def reader(codec: Codec) =
    GrpcProtocolReader(codec, Flow.fromGraph(new GrpcFramingDecoderStage(codec, decodeFrame)))

  private def decodeFrame(frameType: Int, data: ByteString) = DataFrame(data)

  @inline
  private def encodeFrame(codec: Codec, frame: Frame): ChunkStreamPart = {
    val compressedFlag = if (codec == Identity) GrpcProtocol.notCompressed else GrpcProtocol.compressed
    frame match {
      case DataFrame(data)       => Chunk(encodeFrameData(compressedFlag, codec.compress(data)))
      case TrailerFrame(headers) => LastChunk(trailer = headers)
    }
  }

  @inline
  private[grpc] def encodeFrameData(frameType: ByteString, data: ByteString): ByteString = {
    val length = data.length
    frameType ++ ByteString((length >> 24).toByte, (length >> 16).toByte, (length >> 8).toByte, length.toByte) ++ data
  }

  private[grpc] class GrpcFramingDecoderStage(codec: Codec, deframe: (Int, ByteString) => Frame)
      extends ByteStringParser[Frame] {
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new ParsingLogic {
      startWith(ReadFrameHeader)

      trait Step extends ParseStep[Frame]

      object ReadFrameHeader extends Step {
        override def parse(reader: ByteReader): ParseResult[Frame] = {
          val frameType = reader.readByte()
          // If we want to support > 2GB frames, this should be unsigned
          val length = reader.readIntBE()

          if (length == 0) ParseResult(Some(deframe(frameType, ByteString.empty)), ReadFrameHeader)
          else ParseResult(None, ReadFrame(frameType, length), acceptUpstreamFinish = false)
        }
      }

      sealed case class ReadFrame(frameType: Int, length: Int) extends Step {
        val compression = (frameType & 0x01) == 1

        override def parse(reader: ByteReader): ParseResult[Frame] = {
          if (compression) codec match {
            case Identity =>
              failStage(
                new StatusException(
                  Status.INTERNAL.withDescription(
                    "Compressed-Flag bit is set, but a compression encoding is not specified")))
              ParseResult(None, Failed)
            case _ =>
              ParseResult(Some(deframe(frameType, codec.uncompress(reader.take(length)))), ReadFrameHeader)
          }
          else {
            ParseResult(Some(deframe(frameType, reader.take(length))), ReadFrameHeader)
          }
        }
      }

      final case object Failed extends Step {
        override def parse(reader: ByteReader): ParseResult[Frame] = ParseResult(None, Failed)
      }
    }
  }

}
