/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.annotation.{ ApiMayChange, DoNotInherit, InternalApi }
import akka.util.ByteString
import com.google.protobuf.any

/**
 * Immutable representation of the metadata in a call
 *
 * Not for user extension
 */
@ApiMayChange
@DoNotInherit trait Metadata {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[grpc] val raw: Option[io.grpc.Metadata] = None

  /**
   * @return The text header value for `key` if one exists, if the same key has multiple values the last occurrence
   *         that is a text key is used.
   */
  def getText(key: String): Option[String]

  /**
   * @return The binary header value for `key` if one exists, if the same key has multiple values the last occurrence
   *         that is a text key is used.
   */
  def getBinary(key: String): Option[ByteString]

  /**
   * @return The metadata as a map.
   */
  @ApiMayChange
  def asMap: Map[String, List[MetadataEntry]]

  /**
   * @return A list of (key, MetadataEntry) tuples.
   */
  @ApiMayChange
  def asList: List[(String, MetadataEntry)]
}

/**
 * Provides access to details to more rich error details using the logical gRPC com.google.rpc.Status message, see
 * [API Design Guide](https://cloud.google.com/apis/design/errors) for more details.
 *
 * Not for user extension
 */
@ApiMayChange
@DoNotInherit
trait MetadataStatus extends Metadata {
  def status: com.google.rpc.Status
  def code: Int
  def message: String
  def details: Seq[any.Any]
  def getParsedDetails[K <: scalapb.GeneratedMessage](implicit msg: scalapb.GeneratedMessageCompanion[K]): Seq[K]
}
