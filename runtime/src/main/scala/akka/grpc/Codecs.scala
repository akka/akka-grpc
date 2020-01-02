/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.grpc.scaladsl.headers.`Message-Accept-Encoding`
import akka.http.javadsl.{ model => jm }

import scala.collection.immutable

object Codecs {
  // TODO should this list be made user-extensible?
  val supportedCodecs = immutable.Seq(Gzip)

  private val supported = supportedCodecs.map(_.name)
  private val byName = supportedCodecs.map(c => c.name -> c).toMap

  def negotiate(request: jm.HttpRequest): Codec =
    `Message-Accept-Encoding`
      .findIn(request.getHeaders)
      .intersect(supported)
      .headOption
      .map(byName(_))
      .getOrElse(Identity)
}
