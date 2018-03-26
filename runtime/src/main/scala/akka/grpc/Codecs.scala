package akka.grpc

import akka.grpc.scaladsl.headers.`Message-Accept-Encoding`
import akka.http.scaladsl.model.HttpRequest

import scala.collection.immutable

object Codecs {
  // TODO should this list be made user-extensible?
  val supportedCodecs = immutable.Seq(Gzip)

  private val supported = supportedCodecs.map(_.name)
  private val byName = supportedCodecs.map(c ⇒ c.name → c).toMap

  def negotiate(request: HttpRequest): Codec =
    `Message-Accept-Encoding`.findIn(request.headers)
      .intersect(supported)
      .headOption
      .map(byName(_))
      .getOrElse(Identity)
}
