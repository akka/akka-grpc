/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.util.Base64

import akka.annotation.DoNotInherit
import akka.http.scaladsl.model.HttpHeader
import akka.util.ByteString

import scala.collection.immutable

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
  def getText(key: String): Option[String]

  /**
   * @return The binary header value for `key` if one exists, if the same key has multiple values the last occurrence
   *         that is a text key is used.
   */
  def getBinary(key: String): Option[ByteString]

  /**
   * @return The metadata as a map.
   */
  def asMap: MetadataMap
}

class MetadataImpl(headers: immutable.Seq[HttpHeader] = immutable.Seq.empty) extends Metadata {
  lazy private val b64Decoder = Base64.getDecoder
  lazy private val map: MetadataMap = {
    // REVIEWER NOTE: modeled after akka.grpc.internal.MetadataImpl.metadataMapFromGoogleGrpcMetadata
    var entries = Map.empty[String, List[MetadataEntry]]
    headers.foreach { header =>
      val key = header.name()
      val entry =
        if (key.endsWith("-bin")) {
          val bytes = b64Decoder.decode(header.value())
          BytesEntry(ByteString(bytes))
        } else {
          val text = header.value
          StringEntry(text)
        }
      if (entries.contains(key)) {
        entries += (key -> (entry :: entries(key)))
      } else
        entries += (key -> (entry :: Nil))
    }
    entries
  }

  override def getText(key: String): Option[String] = headers.find(_.name == key).map(_.value)

  override def getBinary(key: String): Option[ByteString] = headers.find(_.name == key).map(h => ByteString(b64Decoder.decode(h.value)))

  override def asMap: MetadataMap = map
}
