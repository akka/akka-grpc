/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.NotUsed
import akka.annotation.DoNotInherit
import akka.grpc.{ GrpcClientSettings, GrpcResponseMetadata, GrpcSingleResponse }
import akka.stream.scaladsl.Source
import akka.util.{ ByteString, JavaDurationConverters }

import scala.concurrent.Future
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
   *
   * FIXME for streaming response this doesn't really make sense, left it to keep parity with single response but maybe it should go
   */
  def invoke(request: Req): Source[Res, NotUsed]

  /**
   * Invoke the gRPC method with the additional metadata added and provide access to response metadata
   */
  def invokeWithMetadata(request: Req): Source[Res, Future[GrpcResponseMetadata]]
}
