/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal
import akka.grpc.{ Codec, Codecs, GrpcProtocol }
import akka.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.{ ContentType, MediaType }

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
