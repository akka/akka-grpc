/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

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
import io.grpc.{ Status, StatusException }

object Grpc {
  val contentType = MediaType.applicationBinary("grpc+proto", MediaType.NotCompressible).toContentType

  // Flag to signal the start of an uncompressed frame
  val notCompressed = ByteString(0)
  // Flag to signal the start of an frame compressed according to the Message-Encoding from the header
  val compressed = ByteString(1)

  val grpcFramingEncoder: Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString].map(frame => encodeFrame(notCompressed, frame))
  }

  def grpcFramingEncoder(codec: Codec): Flow[ByteString, ByteString, NotUsed] =
    if (codec == Identity) Flow[ByteString].map(frame => encodeFrame(notCompressed, frame))
    else Flow[ByteString].map(frame => encodeFrame(compressed, codec.compress(frame)))

  @inline
  def encodeFrame(compressedFlag: ByteString, frame: ByteString): ByteString = {
    val length = frame.size
    compressedFlag ++ ByteString((length >> 24).toByte, (length >> 16).toByte, (length >> 8).toByte, length.toByte) ++ frame
  }

  def grpcFramingDecoder(encoding: Option[String]): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(new GrpcFramingDecoderStage(uncompressor(encoding)))

  val grpcFramingDecoder: Flow[ByteString, ByteString, NotUsed] = grpcFramingDecoder(encoding = None)

  private def uncompressor(encoding: Option[String]): Option[ByteString => ByteString] = encoding match {
    case None             => None
    case Some("identity") => None
    case Some("gzip")     => Some(Gzip.uncompress)
    case Some(enc)        => throw new IllegalArgumentException(s"Unknown encoding $enc")
  }

  private class GrpcFramingDecoderStage(uncompressor: Option[ByteString => ByteString])
      extends ByteStringParser[ByteString] {
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

      sealed case class ReadFrame(compression: Boolean, length: Int) extends Step {
        override def parse(reader: ByteStringParser.ByteReader): ParseResult[ByteString] =
          if (compression) uncompressor match {
            case None =>
              failStage(
                new StatusException(
                  Status.INTERNAL.withDescription("Compressed-Flag bit is set, but encoding unknown")))
              ParseResult(None, Failed)
            case Some(uncompress) =>
              ParseResult(Some(uncompress(reader.take(length))), ReadFrameHeader)
          }
          else ParseResult(Some(reader.take(length)), ReadFrameHeader)
      }

      final case object Failed extends Step {
        override def parse(reader: ByteStringParser.ByteReader): ParseResult[ByteString] = ParseResult(None, Failed)
      }
    }
  }
}
