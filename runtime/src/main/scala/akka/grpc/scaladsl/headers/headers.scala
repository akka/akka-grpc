/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl.headers

import akka.annotation.ApiMayChange
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{ ModeledCustomHeader, ModeledCustomHeaderCompanion }
import akka.http.javadsl.{ model => jm }

import scala.collection.immutable
import scala.util.Try

@ApiMayChange
final class `Message-Accept-Encoding`(override val value: String)
    extends ModeledCustomHeader[`Message-Accept-Encoding`] {
  override def renderInRequests = true
  override def renderInResponses = true
  override val companion = `Message-Accept-Encoding`

  lazy val values: Array[String] = value.split(',')
}

@ApiMayChange
object `Message-Accept-Encoding` extends ModeledCustomHeaderCompanion[`Message-Accept-Encoding`] {
  override val name = "grpc-accept-encoding"
  override val lowercaseName: String = super.lowercaseName

  override def parse(value: String): Try[`Message-Accept-Encoding`] =
    Try(new `Message-Accept-Encoding`(value))

  def findIn(headers: Iterable[jm.HttpHeader]): Array[String] =
    headers.collectFirst { case h if h.is(name) => h.value().split(',') }.getOrElse(Array.empty)

  /** Java API */
  def findIn(headers: java.lang.Iterable[jm.HttpHeader]): Array[String] = {
    import scala.jdk.CollectionConverters._
    findIn(headers.asScala)
  }
}

@ApiMayChange
final class `Message-Encoding`(encoding: String) extends ModeledCustomHeader[`Message-Encoding`] {
  override def renderInRequests = true
  override def renderInResponses = true
  override val companion = `Message-Encoding`
  override def value: String = encoding
}

@ApiMayChange
object `Message-Encoding` extends ModeledCustomHeaderCompanion[`Message-Encoding`] {
  override val name = "grpc-encoding"
  override val lowercaseName: String = super.lowercaseName

  override def parse(encoding: String): Try[`Message-Encoding`] = Try(new `Message-Encoding`(encoding))

  def findIn(headers: Iterable[jm.HttpHeader]): Option[String] =
    headers.collectFirst { case h if h.is(name) => h.value() }

  /** Java API */
  def findIn(headers: java.lang.Iterable[jm.HttpHeader]): Option[String] = {
    import scala.jdk.CollectionConverters._
    findIn(headers.asScala)
  }
}

final class `Status`(code: Int) extends ModeledCustomHeader[`Status`] {
  override def renderInRequests = false
  override def renderInResponses = true
  override val companion = `Status`

  override def value() = code.toString
}

object `Status` extends ModeledCustomHeaderCompanion[`Status`] {
  override val name = "grpc-status"
  override val lowercaseName: String = super.lowercaseName

  override def parse(value: String): Try[`Status`] = Try(new `Status`(Integer.parseInt(value)))

  def findIn(headers: immutable.Seq[HttpHeader]): Option[Int] =
    headers.collectFirst { case h if h.is(name) => Integer.parseInt(h.value()) }
}

// grpc-message must be percent encoded: https://github.com/grpc/grpc/issues/4672
final class `Status-Message`(val unencodedValue: String) extends ModeledCustomHeader[`Status-Message`] {
  override def renderInRequests = false
  override def renderInResponses = true
  override val companion = `Status-Message`
  override def value() = PercentEncoding.Encoder.encode(unencodedValue)
}

object `Status-Message` extends ModeledCustomHeaderCompanion[`Status-Message`] {
  override val name = "grpc-message"
  override val lowercaseName: String = super.lowercaseName

  override def parse(value: String): Try[`Status-Message`] = Try(
    new `Status-Message`(PercentEncoding.Decoder.decode(value)))

  def findIn(headers: immutable.Seq[HttpHeader]): Option[String] =
    headers.collectFirst { case h if h.is(name) => h.value() }
}
