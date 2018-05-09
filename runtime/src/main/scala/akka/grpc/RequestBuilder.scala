/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.grpc

import akka.annotation.DoNotInherit
import akka.util.{ ByteString, JavaDurationConverters }

import scala.concurrent.duration.FiniteDuration

/**
 * Request builder for requests providing per call specific metadata capabilities in
 * addition to the client instance default options provided to it through [[GrpcClientSettings]] upon creation.
 *
 * Instances are immutable so can be shared and re-used but are backed by the client that created the instance,
 * so if that is stopped the invocations will fail.
 *
 * Not for user extension
 */
@DoNotInherit
trait RequestBuilder[Req, Res] {

  /** FIXME docs */
  def addMetadata(key: String, value: String): RequestBuilder[Req, Res]

  /** FIXME docs */
  def addMetadata(key: String, value: ByteString): RequestBuilder[Req, Res]

  /**
   * Scala API: FIXME docs
   */
  def withDeadline(deadline: FiniteDuration): RequestBuilder[Req, Res]

  /**
   * Java API: FIXME docs
   */
  def withDeadline(deadline: java.time.Duration): RequestBuilder[Req, Res] =
    withDeadline(JavaDurationConverters.asFiniteDuration(deadline))

  /**
   * Invoke the gRPC method with the additional metadata added
   */
  def invoke(request: Req): Res
}