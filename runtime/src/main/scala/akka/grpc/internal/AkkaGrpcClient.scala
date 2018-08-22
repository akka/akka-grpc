/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage

import akka.Done
import akka.annotation.{ ApiMayChange, InternalApi }
import akka.grpc.GrpcClientSettings
import akka.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }

/**
 * INTERNAL API
 *
 * Public as is included in generated code.
 */
@InternalApi
trait AkkaGrpcClient {
  def close(): Future[Done]
  def closed(): Future[Done]
}

trait AkkaGrpcClientFactory[T <: AkkaGrpcClient] {
  def create(settings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext): T
}

object AkkaGrpcClientFactory {
  /**
   * A function to create an AkkaGrpcClient, bundling its own configuration. These objects are convenient to
   * pass around as implicit values.
   */
  trait Configured[T <: AkkaGrpcClient] {
    /** Create the gRPC client. */
    def create(): T
  }

  /**
   * Bind configuration to a [[AkkaGrpcClientFactory]], creating a [[Configured]].
   */
  def configure[T <: AkkaGrpcClient](
    clientSettings: GrpcClientSettings,
    materializer: Materializer,
    executionContext: ExecutionContext)(implicit factory: AkkaGrpcClientFactory[T]): Configured[T] =
    new Configured[T] {
      override def create(): T = factory.create(clientSettings)(materializer, executionContext)
    }
}

/**
 * INTERNAL API
 *
 * Public as is included in generated code.
 */
@InternalApi
trait JavaAkkaGrpcClient {
  def close(): CompletionStage[Done]
  def closed(): CompletionStage[Done]
}
