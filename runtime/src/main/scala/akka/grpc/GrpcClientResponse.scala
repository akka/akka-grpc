/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc

import java.util.Optional
import java.util.concurrent.CompletionStage

import akka.annotation.DoNotInherit
import io.grpc.Metadata

import scala.concurrent.Future

// FIXME should we provide our own immutable/thread safe response metadata abstraction?

/**
 * Represents the metadata related to a gRPC call with a streaming response
 *
 * Not for user extension
 */
@DoNotInherit
trait GrpcResponseMetadata {
  /**
   * Scala API: The response metadata, the metadata is only for reading and must not be mutated.
   */
  def headers: Metadata
  /**
   * Java API: The response metadata, the metadata is only for reading and must not be mutated.
   */
  def getHeaders(): Metadata

  /**
   * Scala API: Trailers from the server, is completed after the response stream completes
   */
  def trailers: Future[Metadata]
  /**
   * Java API: Trailers from the server, is completed after the response stream completes
   */
  def getTrailers(): CompletionStage[Metadata]
}

/**
 * Represents the metadata related to a gRPC call with a single response value
 *
 * Not for user extension
 */
@DoNotInherit
trait GrpcSingleResponse[T] extends GrpcResponseMetadata {
  /**
   * Scala API: The response body
   */
  def value: T
  /**
   * Java API: The response body
   */
  def getValue(): T
}
