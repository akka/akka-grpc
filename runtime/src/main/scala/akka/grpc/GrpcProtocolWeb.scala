package akka.grpc

import java.util.Base64

import akka.grpc.GrpcProtocolNative.GrpcFramingDecoderStage
import akka.grpc.GrpcProtocol._
import akka.grpc.GrpcWebGen.{ reader, writer }
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

trait GrpcWebVariant extends GrpcProtocol {
  override val contentType: ContentType.Binary

  def postEncode(frame: ByteString): ByteString
  def preDecode(frame: ByteString): ByteString
}

/**
 * Implementation of the gRPC Web protocol.
 */
object GrpcProtocolWeb extends GrpcWebVariant {

  /** Default CORS settings to use for grpc-web */
  val defaultCorsSettings: CorsSettings = CorsSettings.defaultSettings
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

  override val contentType: ContentType.Binary = MediaType.applicationBinary("grpc-web+proto", MediaType.NotCompressible).toContentType
  private val shortMediaType = MediaType.applicationBinary("grpc-web", MediaType.NotCompressible)

  override val mediaTypes: Set[jmodel.MediaType] = Set(contentType.mediaType, shortMediaType)

  override def preDecode(frame: ByteString): ByteString = frame

  override def postEncode(frame: ByteString): ByteString = frame

  /**
   * Obtains a writer for the `application/grpc-web+proto` protocol:
   *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC-web specification.
   *  - Trailer frames are encoded to a [[Chunk]] (containing a marked trailer frame) as per the gRPC-web specification.
   *
   * @param codec the compression codec to apply to data frame contents.
   */
  override def newWriter(codec: Codec): GrpcProtocolWriter = codec match {
    case Identity => grpcWebIdentityWriter
    case Gzip     => grpcWebGzipWriter
    case _        => writer(GrpcProtocolWeb, codec)
  }

  override def newReader(codec: Codec): GrpcProtocolReader = codec match {
    case Identity => grpcWebIdentityReader
    case Gzip     => grpcWebGzipReader
    case _        => reader(GrpcProtocolWeb, codec)
  }

  private val grpcWebIdentityWriter = writer(GrpcProtocolWeb, Identity)
  private val grpcWebGzipWriter = writer(GrpcProtocolWeb, Gzip)
  private val grpcWebIdentityReader = reader(GrpcProtocolWeb, Identity)
  private val grpcWebGzipReader = reader(GrpcProtocolWeb, Gzip)

}

/**
 * The `application/grpc-web-text+proto` variant of gRPC.
 *
 * This is the same as `application/grpc-web+proto`, but with each chunk of the frame encoded gRPC data also base64 encoded.
 */
object GrpcWebTextProtocol extends GrpcWebVariant {

  override val contentType: ContentType.Binary = MediaType.applicationBinary("grpc-web-text+proto", MediaType.Compressible).toContentType
  private val shortMediaType = MediaType.applicationBinary("grpc-web-text", MediaType.Compressible)

  override val mediaTypes: Set[jmodel.MediaType] = Set(contentType.mediaType, shortMediaType)

  override def postEncode(framed: ByteString): ByteString = ByteString(Base64.getEncoder.encode(framed.toByteBuffer))

  override def preDecode(frame: ByteString): ByteString = ByteString(Base64.getDecoder.decode(frame.toByteBuffer))

  override def newWriter(codec: Codec): GrpcProtocolWriter = codec match {
    case Identity => grpcWebTextIdentityWriter
    case Gzip     => grpcWebTextGzipWriter
    case _        => writer(GrpcWebTextProtocol, codec)
  }

  override def newReader(codec: Codec): GrpcProtocolReader = codec match {
    case Identity => grpcWebTextIdentityReader
    case Gzip     => grpcWebTextGzipReader
    case _        => reader(GrpcWebTextProtocol, codec)
  }

  private val grpcWebTextIdentityWriter = writer(GrpcWebTextProtocol, Identity)
  private val grpcWebTextGzipWriter = writer(GrpcWebTextProtocol, Gzip)
  private val grpcWebTextIdentityReader = reader(GrpcWebTextProtocol, Identity)
  private val grpcWebTextGzipReader = reader(GrpcWebTextProtocol, Gzip)

}

private[grpc] object GrpcWebGen {

  private[grpc] def writer(variant: GrpcWebVariant, codec: Codec): GrpcProtocolWriter =
    GrpcProtocolWriter(
      GrpcProtocolNative.adjustCompressibility(variant.contentType, codec),
      codec,
      encodeFrame(variant, codec, _),
      Flow[Frame].map(encodeFrame(variant, codec, _)))

  private[grpc] def reader(variant: GrpcWebVariant, codec: Codec) =
    GrpcProtocolReader(
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
    val framed = GrpcProtocolNative.encodeFrameData(frameType, codec.compress(data))
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
