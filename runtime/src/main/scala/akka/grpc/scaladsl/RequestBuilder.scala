/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.NotUsed
import akka.annotation.{ ApiMayChange, DoNotInherit }
import akka.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import akka.pattern.RetrySettings
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration.Duration

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
@ApiMayChange
trait SingleResponseRequestBuilder[Req, Res] {

  /**
   * Add a header, the value will be ASCII encoded, the same header key can be added multiple times with
   * different values.
   * @return A new request builder, that will pass the added header to the server when invoked
   */
  def addHeader(key: String, value: String): SingleResponseRequestBuilder[Req, Res]

  /**
   * Add a binary header, the same header key can be added multiple times with
   * different values.
   * @return A new request builder, that will pass the added header to the server when invoked
   */
  def addHeader(key: String, value: ByteString): SingleResponseRequestBuilder[Req, Res]

  /**
   * Invoke the gRPC method with the additional metadata added
   */
  def invoke(request: Req): Future[Res]

  /**
   * Invoke the gRPC method with the additional metadata added and provide access to response metadata
   */
  def invokeWithMetadata(request: Req): Future[GrpcSingleResponse[Res]]

  /**
   * Set the deadline for this call
   * @return A new request builder, that will use the supplied deadline when invoked
   */
  def setDeadline(deadline: Duration): SingleResponseRequestBuilder[Req, Res]

  /**
   * Use these retry settings to retry if the call fails.
   */
  def withRetry(retrySettings: RetrySettings): SingleResponseRequestBuilder[Req, Res]

  /**
   * Set the retry settings for this call. A predifined backoff strategy will be calculated based on the number of maxRetries.
   *
   * @param maxRetries The number of retries to make
   */
  def withRetry(maxRetries: Int): SingleResponseRequestBuilder[Req, Res]
}

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
@ApiMayChange
trait StreamResponseRequestBuilder[Req, Res] {

  /**
   * Add a header, the value will be ASCII encoded, the same header key can be added multiple times with
   * different values.
   * @return A new request builder, that will pass the added header to the server when invoked
   */
  def addHeader(key: String, value: String): StreamResponseRequestBuilder[Req, Res]

  /**
   * Add a binary header, the same header key can be added multiple times with
   * different values.
   * @return A new request builder, that will pass the added header to the server when invoked
   */
  def addHeader(key: String, value: ByteString): StreamResponseRequestBuilder[Req, Res]

  /**
   * Invoke the gRPC method with the additional metadata added
   */
  def invoke(request: Req): Source[Res, NotUsed]

  /**
   * Invoke the gRPC method with the additional metadata added and provide access to response metadata
   */
  def invokeWithMetadata(request: Req): Source[Res, Future[GrpcResponseMetadata]]

  /**
   * Set the deadline for this call
   * @return A new request builder, that will use the supplied deadline when invoked
   */
  def setDeadline(deadline: Duration): StreamResponseRequestBuilder[Req, Res]
}
