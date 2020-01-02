/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl.headers

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{ ModeledCustomHeader, ModeledCustomHeaderCompanion }
import akka.http.javadsl.{ model => jm }

import scala.collection.immutable
import scala.util.Try

final class `Message-Accept-Encoding`(override val value: String)
    extends ModeledCustomHeader[`Message-Accept-Encoding`] {
  override def renderInRequests = true
  override def renderInResponses = true
  override val companion = `Message-Accept-Encoding`

  lazy val values: Array[String] = value.split(",")
}

object `Message-Accept-Encoding` extends ModeledCustomHeaderCompanion[`Message-Accept-Encoding`] {
  override val name = "grpc-accept-encoding"
  override def parse(value: String) = Try(new `Message-Accept-Encoding`(value))

  def findIn(headers: Iterable[jm.HttpHeader]): Array[String] =
    headers.find(_.is(name)).map(_.value()).map(_.split(",")).getOrElse(Array.empty)

  /** Java API */
  def findIn(headers: java.lang.Iterable[jm.HttpHeader]): Array[String] = {
    import scala.collection.JavaConverters._
    findIn(headers.asScala)
  }
}

final class `Message-Encoding`(encoding: String) extends ModeledCustomHeader[`Message-Encoding`] {
  override def renderInRequests = true
  override def renderInResponses = true
  override val companion = `Message-Encoding`
  override def value: String = encoding
}

object `Message-Encoding` extends ModeledCustomHeaderCompanion[`Message-Encoding`] {
  override val name = "grpc-encoding"
  override def parse(encoding: String) = Try(new `Message-Encoding`(encoding))

  def findIn(headers: Iterable[jm.HttpHeader]): Option[String] =
    headers.find(_.is(name)).map(_.value())

  /** Java API */
  def findIn(headers: java.lang.Iterable[jm.HttpHeader]): Option[String] = {
    import scala.collection.JavaConverters._
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
  override def parse(value: String) = Try(new `Status`(Integer.parseInt(value)))

  def findIn(headers: immutable.Seq[HttpHeader]): Option[Int] =
    headers.find(_.is(name)).map(h => Integer.parseInt(h.value()))
}

// TODO percent-encoding of message?
final class `Status-Message`(override val value: String) extends ModeledCustomHeader[`Status-Message`] {
  override def renderInRequests = false
  override def renderInResponses = true
  override val companion = `Status-Message`
}

object `Status-Message` extends ModeledCustomHeaderCompanion[`Status-Message`] {
  override val name = "grpc-message"
  override def parse(value: String) = Try(new `Status-Message`(value))

  def findIn(headers: immutable.Seq[HttpHeader]): Option[String] =
    headers.find(_.is(name)).map(_.value())
}
