/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.{ Optional, List => JList, Map => JMap }
import akka.annotation.{ ApiMayChange, DoNotInherit }
import akka.util.ByteString
import akka.japi.Pair
import akka.grpc.scaladsl
import akka.http.javadsl.model.AttributeKey

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
  def asMap(): JMap[String, JList[MetadataEntry]]

  /**
   * @return A list of (key, entry) pairs. Pairs with the same key will be ordered based on when they were added
   *         or received.
   */
  def asList(): JList[Pair[String, MetadataEntry]]

  /**
   * @return Returns the scaladsl.Metadata interface for this instance.
   */
  def asScala: scaladsl.Metadata

  /**
   * Get an attribute from the underlying akka-http message associated with this metadata.
   *
   * Will return `None` if this metadata is not associated with an akka-http request or response, for example,
   * if using the netty client support.
   */
  def getAttribute[T](key: AttributeKey[T]): Optional[T]
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
  def getDetails(): JList[com.google.protobuf.any.Any]
  @deprecated(message = "Use the new getParsedDetails overload taking a Java protobuf message type instead")
  def getParsedDetails[K <: scalapb.GeneratedMessage](companion: scalapb.GeneratedMessageCompanion[K]): JList[K]

  def getParsedDetails[K <: com.google.protobuf.GeneratedMessageV3](defaultMessage: K): JList[K]
}
