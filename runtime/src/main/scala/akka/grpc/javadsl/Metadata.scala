/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.{ List, Map, Optional }
import akka.annotation.{ ApiMayChange, DoNotInherit }
import akka.util.ByteString
import akka.japi.Pair
import akka.grpc.scaladsl

/**
 * Immutable representation of the metadata in a call
 *
 * Not for user extension
 */
@DoNotInherit
@ApiMayChange
trait Metadata {

  /**
   * @return The text header value for `key` if one exists, if the same key has multiple values the last occurrence
   *         that is a text key is used.
   */
  def getText(key: String): Optional[String]

  /**
   * @return The binary header value for `key` if one exists, if the same key has multiple values the last occurrence
   *         that is a text key is used.
   */
  def getBinary(key: String): Optional[ByteString]

  /**
   * @return A map from keys to a list of metadata entries. Entries with the same key will be ordered based on
   *         when they were added or received.
   */
  def asMap(): Map[String, List[MetadataEntry]]

  /**
   * @return A list of (key, entry) pairs. Pairs with the same key will be ordered based on when they were added
   *         or received.
   */
  def asList(): List[Pair[String, MetadataEntry]]

  /**
   * @return Returns the scaladsl.Metadata interface for this instance.
   */
  def asScala: scaladsl.Metadata
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
  def getStatus(): com.google.rpc.Status
  def getCode(): Int
  def getMessage(): String
  def getDetails(): List[com.google.protobuf.any.Any]
  def getParsedDetails[K <: scalapb.GeneratedMessage](companion: scalapb.GeneratedMessageCompanion[K]): List[K]
}
