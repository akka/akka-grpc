/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage

import akka.Done
import akka.annotation.{ ApiMayChange, InternalApi }

import scala.concurrent.Future

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
