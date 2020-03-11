/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal
import akka.NotUsed
import akka.grpc.{ Codec, Codecs, GrpcProtocol, Identity }
import akka.grpc.GrpcProtocol.{ Frame, GrpcProtocolReader, GrpcProtocolWriter }
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.http.scaladsl.model.{ ContentType, MediaType }
import akka.stream.Attributes
import akka.stream.impl.io.ByteStringParser
import akka.stream.impl.io.ByteStringParser.{ ByteReader, ParseResult, ParseStep }
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStageLogic
import akka.util.ByteString
import io.grpc.{ Status, StatusException }

abstract class AbstractGrpcProtocol(subType: String) extends GrpcProtocol {

  override val contentType: ContentType.Binary =
    MediaType.applicationBinary(s"$subType+proto", MediaType.Compressible).toContentType

  override val mediaTypes: Set[jmodel.MediaType] =
    Set(contentType.mediaType, MediaType.applicationBinary(subType, MediaType.Compressible))

  private lazy val knownWriters = Codecs.supportedCodecs.map(c => c -> writer(c)).toMap.withDefault(writer)
  private lazy val knownReaders = Codecs.supportedCodecs.map(c => c -> reader(c)).toMap.withDefault(reader)

  /**
   * Obtains a writer for this protocol:
   * @param codec the compression codec to apply to data frame contents.
   */
  override def newWriter(codec: Codec): GrpcProtocolWriter = knownWriters(codec)

  /**
   * Obtains a reader for this protocol.
   *
   * @param codec the codec to use for compressed frames.
   */
  override def newReader(codec: Codec): GrpcProtocolReader = knownReaders(codec)

  protected def writer(codec: Codec): GrpcProtocolWriter

  protected def reader(codec: Codec): GrpcProtocolReader

}
object AbstractGrpcProtocol {

  /** Field marker to signal the start of an uncompressed frame */
  private val notCompressed: ByteString = ByteString(0)

  /** Field marker to signal the start of an frame compressed according to the Message-Encoding from the header */
  private val compressed: ByteString = ByteString(1)

  def fieldType(codec: Codec) = if (codec == Identity) notCompressed else compressed

  /**
   * Adjusts thye compressibility of a content type to suit a message encoding.
   * @param contentType the content type for the gRPC protocol.
   * @param codec the message encoding being used to encode objects.
   * @return the provided content type, with the compressibility adapted to reflect whether HTTP transport level compression should be used.
   */
  private def adjustCompressibility(contentType: ContentType.Binary, codec: Codec): ContentType.Binary =
    contentType.mediaType
      .withComp(codec match {
        case Identity => MediaType.Compressible
        case _        => MediaType.NotCompressible
      })
      .toContentType

  @inline
  def encodeFrameData(frameType: ByteString, data: ByteString): ByteString = {
    val length = data.length
    frameType ++ ByteString((length >> 24).toByte, (length >> 16).toByte, (length >> 8).toByte, length.toByte) ++ data
  }

  def writer(protocol: GrpcProtocol, codec: Codec, encodeFrame: Frame => ChunkStreamPart): GrpcProtocolWriter =
    GrpcProtocolWriter(
      adjustCompressibility(protocol.contentType, codec),
      codec,
      encodeFrame,
      Flow[Frame].map(encodeFrame))

  def reader(
      codec: Codec,
      decodeFrame: (Int, ByteString) => Frame,
      flowAdapter: Flow[ByteString, Frame, NotUsed] => Flow[ByteString, Frame, NotUsed] = identity)
      : GrpcProtocolReader =
    GrpcProtocolReader(codec, flowAdapter(Flow.fromGraph(new GrpcFramingDecoderStage(codec, decodeFrame))))

  class GrpcFramingDecoderStage(codec: Codec, deframe: (Int, ByteString) => Frame) extends ByteStringParser[Frame] {
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
        private val compression = (frameType & 0x01) == 1

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
