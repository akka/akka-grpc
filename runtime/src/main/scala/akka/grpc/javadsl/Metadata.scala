/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.{ Base64, Optional }

import akka.annotation.DoNotInherit
import akka.http.javadsl.model.HttpHeader
import akka.util.ByteString
import java.lang.{ Iterable => jIterable }
import scala.collection.JavaConverters._

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

class MetadataImpl(jHeaders: jIterable[HttpHeader]) extends Metadata {
  private val headers = jHeaders.asScala

  override def getText(key: String): Optional[String] =
    headers.collectFirst {
      case header if header.name == key => header.value
    } match {
      case Some(v) => Optional.of(v)
      case None    => Optional.empty[String]
    }

  override def getBinary(key: String): Optional[ByteString] =
    headers.collectFirst {
      case header if header.name == key =>
        ByteString(Base64.getDecoder.decode(header.value))
    } match {
      case Some(v) => Optional.of(v)
      case None    => Optional.empty[ByteString]
    }
}
