/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.grpc.scaladsl.headers.{ `Message-Accept-Encoding`, `Message-Encoding` }
import akka.http.javadsl.{ model => jm }
import io.grpc.Status

import scala.collection.immutable
import scala.util.{ Failure, Success, Try }

object Codecs {
  // TODO should this list be made user-extensible?
  val supportedCodecs = immutable.Seq(Gzip, Identity)

  private val supported = supportedCodecs.map(_.name)
  private val byName = supportedCodecs.map(c => c.name -> c).toMap

  val supportedMessageAcceptEncoding: `Message-Accept-Encoding` = `Message-Accept-Encoding`(supported.mkString(","))

  /**
   * Determines the message encoding to use for a server response to a client.
   *
   * @param request the gRPC client request.
   * @return a codec to compress data frame bodies with, which will be [[Identity]] unless the client specifies support for another supported encoding.
   */
  def negotiate(request: jm.HttpRequest): Codec =
    `Message-Accept-Encoding`
      .findIn(request.getHeaders)
      .intersect(supported)
      .headOption
      .map(byName(_))
      .getOrElse(Identity)

  /**
   * Determines the `Message-Encoding` specified in a request.
   *
   * @param request the gRPC request
   * @return the specified codec to uncompress data frame bodies with, [[Identity]] if no encoding was specified, or [[Failure]] if an unsupported encoding was specified.
   */
  def detect(request: jm.HttpRequest): Try[Codec] =
    detect(`Message-Encoding`.findIn(request.getHeaders).headOption)

  /**
   * Determines the `Message-Encoding` specified in a gRPC stream to be unmarshalled.
   *
   * @param encoding the specified message encoding.
   * @return the specified codec to uncompress data frame bodies with, [[Identity]] if no encoding was specified, or [[Failure]] if an unsupported encoding was specified.
   */
  def detect(encoding: Option[String]): Try[Codec] =
    encoding
      .map { codec =>
        byName
          .get(codec)
          .map(Success(_))
          .getOrElse(Failure(new GrpcServiceException(
            Status.UNIMPLEMENTED.withDescription(s"Message Encoding $encoding is not supported"))))
      }
      .getOrElse(Success(Identity))
}
