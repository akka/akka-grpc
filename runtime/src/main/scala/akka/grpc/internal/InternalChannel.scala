/*
 * Copyright (C) 2009-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.Future
import akka.{ Done, NotUsed }
import akka.annotation.InternalApi
import akka.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import akka.stream.scaladsl.Source
import io.grpc.{ CallOptions, MethodDescriptor }

/**
 * INTERNAL API
 */
@InternalApi
abstract class InternalChannel {
  def invoke[I, O](
      request: I,
      headers: MetadataImpl,
      descriptor: MethodDescriptor[I, O],
      options: CallOptions): Future[O]
  def invokeWithMetadata[I, O](
      request: I,
      headers: MetadataImpl,
      descriptor: MethodDescriptor[I, O],
      options: CallOptions): Future[GrpcSingleResponse[O]]

  def invokeWithMetadata[I, O](
      source: Source[I, NotUsed],
      headers: MetadataImpl,
      descriptor: MethodDescriptor[I, O],
      streamingResponse: Boolean,
      options: CallOptions): Source[O, Future[GrpcResponseMetadata]]

  def shutdown(): Unit
  def done: Future[Done]
}
