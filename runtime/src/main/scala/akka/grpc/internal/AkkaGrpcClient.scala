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
  def apply(settings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext): T
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
