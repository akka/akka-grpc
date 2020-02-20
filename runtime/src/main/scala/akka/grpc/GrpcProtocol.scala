package akka.grpc

import akka.NotUsed
import akka.grpc.GrpcProtocol.{ GrpcProtocolMarshaller, GrpcProtocolUnmarshaller }
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.{ ContentType, HttpHeader }
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scala.util.Try

/**
 * A variant of the gRPC protocol - e.g. gRPC and gRPC-Web
 */
trait GrpcVariant {

  /** The canonical media type to use for this protocol variant */
  val contentType: ContentType

  /** The set of media types that can identify this protocol variant (e.g. including an implicit +proto) */
  val mediaTypes: Set[jmodel.MediaType]

  /**
   * Constructs a marshaller for writing gRPC protocol frames for this variant
   * @param codec the compression codec to encode data frame bodies with.
   */
  def newMarshaller(codec: Codec): GrpcProtocolMarshaller

  /**
   * Constructs an unmarshaller for reading gRPC protocol frames for this variant.
   * @param codec the compression codec to decode data frame bodies with.
   */
  def newUnmarshaller(codec: Codec): GrpcProtocolUnmarshaller
}

/**
 * Core definitions for gRPC protocols.
 */
object GrpcProtocol {

  private val grpcVariants: Seq[GrpcVariant] = Seq(Grpc, GrpcWeb, GrpcWebText)

  /** Field marker to signal the start of an uncompressed frame */
  val notCompressed: ByteString = ByteString(0)

  /** Field marker to signal the start of an frame compressed according to the Message-Encoding from the header */
  val compressed: ByteString = ByteString(1)

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
  case class GrpcProtocolMarshaller(
      /** The media type produced by this marshaller */
      contentType: ContentType,
      /** The compression codec to be used for data frame bodies */
      messageEncoding: Codec,
      /** Encodes a frame as a part in a chunk stream. */
      encodeFrame: Frame => ChunkStreamPart,
      /** A Flow over a stream of Frame using this frame encoding */
      frameEncoder: Flow[Frame, ChunkStreamPart, NotUsed])

  /**
   * Implements the decoding of the gRPC framing from a physical/transport layer.
   */
  case class GrpcProtocolUnmarshaller(
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
   * @return a [[GrpcVariant]] matching the request mediatype if specified and known.
   */
  def detect(request: jmodel.HttpRequest): Option[GrpcVariant] = detect(request.entity.getContentType.mediaType)

  /**
   * Detects which gRPC protocol variant is indicated by a mediatype.
   * @return a [[GrpcVariant]] matching the request mediatype if known.
   */
  def detect(mediaType: jmodel.MediaType): Option[GrpcVariant] = grpcVariants.find(_.mediaTypes.contains(mediaType))

  /**
   * Calculates the gRPC protocol encoding to use for an interaction with a gRPC client.
   *
   * @param request the client request to respond to.
   * @return the request unmarshaller, and the response marshaller.
   */
  def negotiate(request: jmodel.HttpRequest): Option[(Try[GrpcProtocolUnmarshaller], GrpcProtocolMarshaller)] =
    detect(request).map { variant =>
      (Codecs.detect(request).map(variant.newUnmarshaller), variant.newMarshaller(Codecs.negotiate(request)))
    }

}
