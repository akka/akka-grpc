/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.Future
import akka.Done
import akka.annotation.InternalApi
import akka.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import akka.stream.scaladsl.{ Flow, Source }
import akka.util.OptionVal
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
      source: I,
      defaultFlow: OptionVal[Flow[I, O, Future[GrpcResponseMetadata]]],
      fqMethodName: String,
      headers: MetadataImpl,
      descriptor: MethodDescriptor[I, O],
      options: CallOptions): Source[O, Future[GrpcResponseMetadata]]

  def createFlow[I, O](
      fqMethodName: String,
      headers: MetadataImpl,
      descriptor: MethodDescriptor[I, O],
      streamingResponse: Boolean,
      options: CallOptions): Flow[I, O, Future[GrpcResponseMetadata]]

  def shutdown(): Unit
  def done: Future[Done]
}
