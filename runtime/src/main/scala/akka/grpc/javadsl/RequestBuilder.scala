/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.javadsl

import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.annotation.DoNotInherit
import akka.grpc.{ GrpcClientSettings, GrpcResponseMetadata, GrpcSingleResponse }
import akka.stream.javadsl.Source
import akka.util.ByteString

import scala.concurrent.Future

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

  /** FIXME docs */
  def addMetadata(key: String, value: String): SingleResponseRequestBuilder[Req, Res]

  /** FIXME docs */
  def addMetadata(key: String, value: ByteString): SingleResponseRequestBuilder[Req, Res]

  /**
   * FIXME docs
   */
  def withDeadline(deadline: java.time.Duration): SingleResponseRequestBuilder[Req, Res]

  /**
   * Invoke the gRPC method with the additional metadata added
   */
  def invoke(request: Req): CompletionStage[Res]

  /**
   * Invoke the gRPC method with the additional metadata added and provide access to response metadata
   */
  def invokeWithMetadata(request: Req): CompletionStage[GrpcSingleResponse[Res]]
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

  /** FIXME docs */
  def addMetadata(key: String, value: String): StreamResponseRequestBuilder[Req, Res]

  /** FIXME docs */
  def addMetadata(key: String, value: ByteString): StreamResponseRequestBuilder[Req, Res]

  /**
   * FIXME docs
   */
  def withDeadline(deadline: java.time.Duration): StreamResponseRequestBuilder[Req, Res]

  /**
   * Invoke the gRPC method with the additional metadata added
   */
  def invoke(request: Req): Source[Res, NotUsed]

  /**
   * Invoke the gRPC method with the additional metadata added and provide access to response metadata
   */
  def invokeWithMetadata(request: Req): Source[Res, CompletionStage[GrpcResponseMetadata]]
}
