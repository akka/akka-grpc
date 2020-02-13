/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.NotUsed
import akka.grpc.GrpcProtocol.{ GrpcProtocolMarshaller, _ }
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart, LastChunk }
import akka.http.scaladsl.model._
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
object Grpc extends GrpcVariant {

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
   * Obtains a marshaller for the `application/grpc+proto` protocol:
   *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC specification
   *  - Trailer frames are encoded to [[LastChunk]], to be rendered into the underlying HTTP/2 transport
   *
   * @param codec the compression codec to apply to data frame contents.
   */
  override def newMarshaller(codec: Codec): GrpcProtocolMarshaller = codec match {
    case Identity => grpcIdentityMarshaller
    case Gzip     => grpcGzipMarshaller
    case _        => marshaller(codec)
  }

  /**
   * Obtains an unmarshaller for the `application/grpc+proto` protocol.
   *
   * @param codec the codec to use for compressed frames.
   */
  override def newUnmarshaller(codec: Codec): GrpcProtocolUnmarshaller = codec match {
    case Identity => grpcIdentityUnMarshaller
    case Gzip     => grpcGzipUnMarshaller
    case _        => unmarshaller(codec)
  }

  private val grpcIdentityMarshaller = marshaller(Identity)
  private val grpcGzipMarshaller = marshaller(Gzip)
  private val grpcIdentityUnMarshaller = unmarshaller(Identity)
  private val grpcGzipUnMarshaller = unmarshaller(Gzip)

  private def marshaller(codec: Codec) =
    GrpcProtocolMarshaller(
      adjustCompressibility(Grpc.contentType, codec),
      codec,
      Grpc.encodeFrame(codec, _),
      Flow[Frame].map(Grpc.encodeFrame(codec, _)))

  private def unmarshaller(codec: Codec) =
    GrpcProtocolUnmarshaller(codec, Flow.fromGraph(new GrpcFramingDecoderStage(codec, decodeFrame)))

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
