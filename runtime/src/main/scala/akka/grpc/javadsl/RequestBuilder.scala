/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.time.Duration
import java.util.concurrent.CompletionStage
import akka.NotUsed
import akka.annotation.{ ApiMayChange, DoNotInherit }
import akka.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import akka.pattern.RetrySettings
import akka.stream.javadsl.Source
import akka.util.ByteString

/**
 * Request builder for requests providing per call specific metadata capabilities in
 * addition to the client instance default options provided to it through [[akka.grpc.GrpcClientSettings]] upon creation.
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
  def invoke(request: Req): CompletionStage[Res]

  /**
   * Invoke the gRPC method with the additional metadata added and provide access to response metadata
   */
  def invokeWithMetadata(request: Req): CompletionStage[GrpcSingleResponse[Res]]

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
   * Set the retry settings for this call. A predefined backoff strategy will be calculated based on the number of maxRetries.
   *
   * @param maxRetries The number of retries to make
   */
  def withRetry(maxRetries: Int): SingleResponseRequestBuilder[Req, Res]
}

/**
 * Request builder for requests providing per call specific metadata capabilities in
 * addition to the client instance default options provided to it through [[akka.grpc.GrpcClientSettings]] upon creation.
 *
 * Instances are immutable so can be shared and re-used but are backed by the client that created the instance,
 * so if that is stopped the invocations will fail.
 *
 * Only expected to be useful and generated for Java 21 and later where blocking can be done safely on virtual
 * threads.
 *
 * Not for user extension
 */
@DoNotInherit
@ApiMayChange
trait SingleBlockingResponseRequestBuilder[Req, Res] {

  /**
   * Add a header, the value will be ASCII encoded, the same header key can be added multiple times with
   * different values.
   * @return A new request builder, that will pass the added header to the server when invoked
   */
  def addHeader(key: String, value: String): SingleBlockingResponseRequestBuilder[Req, Res]

  /**
   * Add a binary header, the same header key can be added multiple times with
   * different values.
   * @return A new request builder, that will pass the added header to the server when invoked
   */
  def addHeader(key: String, value: ByteString): SingleBlockingResponseRequestBuilder[Req, Res]

  /**
   * Invoke the gRPC method with the additional metadata added
   */
  def invoke(request: Req): Res

  /**
   * Invoke the gRPC method with the additional metadata added
   */
  def invokeAsync(request: Req): CompletionStage[Res]

  /**
   * Invoke the gRPC method with the additional metadata added and provide access to response metadata
   */
  def invokeWithMetadata(request: Req): GrpcSingleResponse[Res]

  /**
   * Invoke the gRPC method with the additional metadata added and provide access to response metadata
   */
  def invokeWithMetadataAsync(request: Req): CompletionStage[GrpcSingleResponse[Res]]

  /**
   * Set the deadline for this call
   * @return A new request builder, that will use the supplied deadline when invoked
   */
  def setDeadline(deadline: Duration): SingleBlockingResponseRequestBuilder[Req, Res]

  /**
   * Use these retry settings to retry if the call fails.
   */
  def withRetry(retrySettings: RetrySettings): SingleBlockingResponseRequestBuilder[Req, Res]

  /**
   * Set the retry settings for this call. A predifined backoff strategy will be calculated based on the number of maxRetries.
   *
   * @param maxRetries The number of retries to make
   */
  def withRetry(maxRetries: Int): SingleBlockingResponseRequestBuilder[Req, Res]
}

/**
 * Request builder for requests providing per call specific metadata capabilities in
 * addition to the client instance default options provided to it through [[akka.grpc.GrpcClientSettings]] upon creation.
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
  def invokeWithMetadata(request: Req): Source[Res, CompletionStage[GrpcResponseMetadata]]

  /**
   * Set the deadline for this call
   * @return A new request builder, that will use the supplied deadline when invoked
   */
  def setDeadline(deadline: Duration): StreamResponseRequestBuilder[Req, Res]
}
