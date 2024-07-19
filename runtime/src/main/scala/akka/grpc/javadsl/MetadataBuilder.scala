/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.lang.{ Iterable => jIterable }
import akka.annotation.ApiMayChange

import scala.collection.JavaConverters._
import akka.http.javadsl.model.{ HttpHeader, HttpMessage }
import akka.http.scaladsl.model.{ HttpHeader => sHttpHeader, HttpMessage => sHttpMessage }
import akka.http.scaladsl.model.headers.RawHeader
import akka.util.ByteString
import akka.grpc.scaladsl
import akka.grpc.internal.JavaMetadataImpl

/**
 * This class provides an interface for constructing immutable Metadata instances.
 */
@ApiMayChange
class MetadataBuilder {
  private val delegate = new scaladsl.MetadataBuilder

  /**
   * Adds a string entry. The key must not end in the "-bin" binary suffix.
   * @param key The entry key.
   * @param value The entry value.
   * @return The updated builder.
   */
  def addText(key: String, value: String): MetadataBuilder = {
    delegate.addText(key, value)
    this
  }

  /**
   * Adds a binary entry. The key must end in the "-bin" binary suffix.
   * @param key The entry key.
   * @param value The entry value.
   * @return The updated builder.
   */
  def addBinary(key: String, value: ByteString): MetadataBuilder = {
    delegate.addBinary(key, value)
    this
  }

  /**
   * Builds the immutable metadata instance.
   * @return The instance.
   */
  def build(): Metadata =
    new JavaMetadataImpl(delegate.build())
}

@ApiMayChange
object MetadataBuilder {

  /**
   * @return An empty metadata instance.
   */
  val empty: Metadata = new JavaMetadataImpl(scaladsl.MetadataBuilder.empty)

  /**
   * Constructs a Metadata instance from a collection of HTTP headers.
   * @param headers The headers.
   * @return The metadata instance.
   */
  def fromHeaders(headers: jIterable[HttpHeader]): Metadata =
    new JavaMetadataImpl(scaladsl.MetadataBuilder.fromHeaders(headers.asScala.map(asScala).toList))

  /**
   * Constructs a Metadata instance from an HTTP message.
   *
   * @param message The HTTP message.
   * @return The metadata instance.
   */
  def fromHttpMessage(message: HttpMessage): Metadata = {
    message match {
      // All HTTP messages should be scaladsl HttpMessages
      case s: sHttpMessage => new JavaMetadataImpl(scaladsl.MetadataBuilder.fromHttpMessage(s))
      // If not, just pass the headers
      case _ => fromHeaders(message.getHeaders)
    }

  }

  /**
   * Converts from a javadsl.HttpHeader to a scaladsl.HttpHeader.
   * @param header A Java HTTP header.
   * @return An equivalent Scala HTTP header.
   */
  private def asScala(header: HttpHeader): sHttpHeader =
    header match {
      case s: sHttpHeader => s
      case _              => RawHeader(header.name, header.value)
    }
}
