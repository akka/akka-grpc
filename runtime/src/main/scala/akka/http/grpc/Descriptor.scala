package akka.http.grpc

import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.{ Attributes, Materializer }
import akka.stream.impl.io.ByteStringParser
import akka.stream.impl.io.ByteStringParser.{ ParseResult, ParseStep }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.stage.GraphStageLogic
import akka.util.ByteString
import com.trueaccord.scalapb.{ GeneratedMessage, GeneratedMessageCompanion, Message }

import scala.concurrent.{ ExecutionContext, Future }

// TODO separate it into "runtime" library;
// TODO go over ByteBuffers so we avoid copying?
trait ProtobufSerializer[T] {
  def serialize(t: T): ByteString
  def deserialize(bytes: ByteString): T
}

class ScalapbProtobufSerializer[T <: GeneratedMessage with Message[T]](companion: GeneratedMessageCompanion[T]) extends ProtobufSerializer[T] {
  override def serialize(t: T) = ByteString(companion.toByteArray(t))
  override def deserialize(bytes: ByteString): T = companion.parseFrom(bytes.iterator.asInputStream)
}

// TODO separate it into "runtime" library;
object Grpc {
  val contentType = MediaType.applicationBinary("grpc+proto", MediaType.NotCompressible).toContentType

  val notCompressed = ByteString(0)

  def grpcFramingEncoder: Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString].map { frame =>

      // todo handle compression

      val length = frame.size
      notCompressed ++ ByteString(
        (length >> 24).toByte,
        (length >> 16).toByte,
        (length >> 8).toByte,
        length.toByte) ++ frame
    }
  }

  def grpcFramingDecoder: Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new GrpcFramingDecoderStage)
  }

  class GrpcFramingDecoderStage extends ByteStringParser[ByteString] {
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

      case class ReadFrame(compression: Boolean, length: Int) extends Step {
        override def parse(reader: ByteStringParser.ByteReader): ParseResult[ByteString] = {
          // todo handle compression
          ParseResult(Some(reader.take(length)), ReadFrameHeader)
        }
      }
    }
  }

}
