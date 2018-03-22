package akka.grpc

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.stream.Attributes
import akka.stream.impl.io.ByteStringParser
import akka.stream.impl.io.ByteStringParser.ParseResult
import akka.stream.impl.io.ByteStringParser.ParseStep
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStageLogic
import akka.util.ByteString

object Grpc {
  val contentType = MediaType.applicationBinary("grpc+proto", MediaType.NotCompressible).toContentType

  // Flag to signal the start of an uncompressed frame
  val notCompressed = ByteString(0)

  def grpcFramingEncoder: Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString].map(encodeFrame)
  }

  def encodeFrame(frame: ByteString): ByteString = {
    // TODO handle compression
    val length = frame.size
    notCompressed ++ ByteString(
      (length >> 24).toByte,
      (length >> 16).toByte,
      (length >> 8).toByte,
      length.toByte) ++ frame
  }

  val grpcFramingDecoder: Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(new GrpcFramingDecoderStage)

  private class GrpcFramingDecoderStage extends ByteStringParser[ByteString] {
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new ParsingLogic {
      startWith(ReadFrameHeader)

      trait Step extends ParseStep[ByteString]

      object ReadFrameHeader extends Step {
        override def parse(reader: ByteStringParser.ByteReader): ByteStringParser.ParseResult[ByteString] = {
          val compression = reader.readByte()
          // If we want to support > 2GB frames, this should be unsigned
          val length = reader.readIntBE()

          if (length == 0) ParseResult(Some(ByteString.empty), ReadFrameHeader)
          else ParseResult(None, ReadFrame(compression == 1, length), acceptUpstreamFinish = false)
        }
      }

      final case class ReadFrame(compression: Boolean, length: Int) extends Step {
        override def parse(reader: ByteStringParser.ByteReader): ParseResult[ByteString] = {
          // todo handle compression
          ParseResult(Some(reader.take(length)), ReadFrameHeader)
        }
      }
    }
  }

}
