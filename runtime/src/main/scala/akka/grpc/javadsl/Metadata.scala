/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.Optional

import akka.annotation.DoNotInherit
import akka.util.ByteString

/**
 * Immutable representation of the metadata in a call
 *
 * Not for user extension
 */
@DoNotInherit trait Metadata {
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
}
