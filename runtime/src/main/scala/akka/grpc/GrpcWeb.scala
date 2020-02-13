package akka.grpc

import java.util.Base64

import akka.grpc.Grpc.GrpcFramingDecoderStage
import akka.grpc.GrpcProtocol._
import akka.grpc.GrpcWebGen.{ marshaller, unmarshaller }
import akka.grpc.scaladsl.headers
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart }
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ `Accept-Encoding`, `Content-Encoding`, `Content-Type`, Accept }
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import ch.megard.akka.http.cors.scaladsl.model.HttpHeaderRange
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.grpc.{ Status, StatusException }

import scala.collection.immutable

trait GrpcWebVariant extends GrpcVariant {
  override val contentType: ContentType.Binary

  def postEncode(frame: ByteString): ByteString
  def preDecode(frame: ByteString): ByteString
}

/**
 * Implementation of the gRPC Web protocol.
 */
object GrpcWeb extends GrpcWebVariant {

  /** Default CORS settings to use for grpc-web */
  val defaultCorsSettings = CorsSettings.defaultSettings
    .withAllowCredentials(true)
    .withAllowedMethods(immutable.Seq(HttpMethods.POST, HttpMethods.OPTIONS))
    .withExposedHeaders(immutable.Seq(headers.`Status`.name, headers.`Status-Message`.name, `Content-Encoding`.name))
    .withAllowedHeaders(
      HttpHeaderRange(
        "x-user-agent",
        "x-grpc-web",
        `Content-Type`.name,
        Accept.name,
        "grpc-timeout",
        `Accept-Encoding`.name))

  override val contentType = MediaType.applicationBinary("grpc-web+proto", MediaType.NotCompressible).toContentType
  private val shortMediaType = MediaType.applicationBinary("grpc-web", MediaType.NotCompressible)

  override val mediaTypes: Set[jmodel.MediaType] = Set(contentType.mediaType, shortMediaType)

  override def preDecode(frame: ByteString): ByteString = frame

  override def postEncode(frame: ByteString): ByteString = frame

  /**
   * Obtains a marshaller for the `application/grpc-web+proto` protocol:
   *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC-web specification.
   *  - Trailer frames are encoded to a [[Chunk]] (containing a marked trailer frame) as per the gRPC-web specification.
   *
   * @param codec the compression codec to apply to data frame contents.
   */
  override def newMarshaller(codec: Codec): GrpcProtocolMarshaller = codec match {
    case Identity => grpcWebIdentityMarshaller
    case Gzip     => grpcWebGzipMarshaller
    case _        => marshaller(GrpcWeb, codec)
  }

  override def newUnmarshaller(codec: Codec): GrpcProtocolUnmarshaller = codec match {
    case Identity => grpcWebIdentityUnmarshaller
    case Gzip     => grpcWebGzipUnmarshaller
    case _        => unmarshaller(GrpcWeb, codec)
  }

  private val grpcWebIdentityMarshaller = marshaller(GrpcWeb, Identity)
  private val grpcWebGzipMarshaller = marshaller(GrpcWeb, Gzip)
  private val grpcWebIdentityUnmarshaller = unmarshaller(GrpcWeb, Identity)
  private val grpcWebGzipUnmarshaller = unmarshaller(GrpcWeb, Gzip)

}

/**
 * The ``application/grpc-web-text+proto`` variant of gRPC.
 *
 * This is the same as ``application/grpc-web+proto``, but with each chunk of the frame encoded gRPC data also base64 encoded.
 */
object GrpcWebText extends GrpcWebVariant {

  override val contentType = MediaType.applicationBinary("grpc-web-text+proto", MediaType.Compressible).toContentType
  private val shortMediaType = MediaType.applicationBinary("grpc-web-text", MediaType.Compressible)

  override val mediaTypes: Set[jmodel.MediaType] = Set(contentType.mediaType, shortMediaType)

  override def postEncode(framed: ByteString): ByteString = ByteString(Base64.getEncoder.encode(framed.toByteBuffer))

  override def preDecode(frame: ByteString): ByteString = ByteString(Base64.getDecoder.decode(frame.toByteBuffer))

  override def newMarshaller(codec: Codec): GrpcProtocolMarshaller = codec match {
    case Identity => grpcWebTextIdentityMarshaller
    case Gzip     => grpcWebTextGzipMarshaller
    case _        => marshaller(GrpcWebText, codec)
  }

  override def newUnmarshaller(codec: Codec): GrpcProtocolUnmarshaller = codec match {
    case Identity => grpcWebTextIdentityUnmarshaller
    case Gzip     => grpcWebTextGzipUnmarshaller
    case _        => unmarshaller(GrpcWebText, codec)
  }

  // Pre-defined supported marshallers
  private val grpcWebTextIdentityMarshaller = marshaller(GrpcWebText, Identity)
  private val grpcWebTextGzipMarshaller = marshaller(GrpcWebText, Gzip)
  private val grpcWebTextIdentityUnmarshaller = unmarshaller(GrpcWebText, Identity)
  private val grpcWebTextGzipUnmarshaller = unmarshaller(GrpcWebText, Gzip)

}

private[grpc] object GrpcWebGen {

  private[grpc] def marshaller(variant: GrpcWebVariant, codec: Codec): GrpcProtocolMarshaller =
    GrpcProtocolMarshaller(
      Grpc.adjustCompressibility(variant.contentType, codec),
      codec,
      encodeFrame(variant, codec, _),
      Flow[Frame].map(encodeFrame(variant, codec, _)))

  private[grpc] def unmarshaller(variant: GrpcWebVariant, codec: Codec) =
    GrpcProtocolUnmarshaller(
      codec,
      Flow[ByteString]
        .map(frame => variant.preDecode(frame))
        .via(Flow.fromGraph(new GrpcFramingDecoderStage(codec, decodeFrame))))

  @inline
  private def encodeFrame(variant: GrpcWebVariant, codec: Codec, frame: Frame): ChunkStreamPart = {
    val dataFrameType = if (codec == Identity) GrpcProtocol.notCompressed else GrpcProtocol.compressed
    val (frameType, data) = frame match {
      case DataFrame(data)       => (dataFrameType, data)
      case TrailerFrame(trailer) => (ByteString(dataFrameType(0) | 0x80), encodeTrailer(trailer))
    }
    val framed = Grpc.encodeFrameData(frameType, codec.compress(data))
    Chunk(variant.postEncode(framed))
  }

  @inline
  private def decodeFrame(frameHeader: Int, data: ByteString): Frame = {
    (frameHeader & 80) match {
      case 0 => DataFrame(data)
      case 1 => TrailerFrame(decodeTrailer(data))
      case f => throw new StatusException(Status.INTERNAL.withDescription("Unknown frame type $f"))
    }
  }

  private val CrLf = "\r\n"

  @inline
  private def encodeTrailer(trailer: Seq[HttpHeader]): ByteString = ByteString(trailer.mkString("", CrLf, CrLf))

  @inline
  private def decodeTrailer(data: ByteString): List[HttpHeader] = ???

}
