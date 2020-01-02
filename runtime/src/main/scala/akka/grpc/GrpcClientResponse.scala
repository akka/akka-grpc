/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.util.concurrent.CompletionStage

import akka.annotation.DoNotInherit

import scala.concurrent.Future

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
  def headers: akka.grpc.scaladsl.Metadata

  /**
   * Java API: The response metadata, the metadata is only for reading and must not be mutated.
   */
  def getHeaders(): akka.grpc.javadsl.Metadata

  /**
   * Scala API: Trailers from the server, is completed after the response stream completes
   */
  def trailers: Future[akka.grpc.scaladsl.Metadata]

  /**
   * Java API: Trailers from the server, is completed after the response stream completes
   */
  def getTrailers(): CompletionStage[akka.grpc.javadsl.Metadata]
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
