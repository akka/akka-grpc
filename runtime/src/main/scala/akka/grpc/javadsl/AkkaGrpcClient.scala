/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletionStage

import akka.Done
import akka.annotation.DoNotInherit

/** Common trait of all generated Akka gRPC clients. Not for user extension. */
@DoNotInherit
trait AkkaGrpcClient {

  /**
   * Initiates a shutdown in which preexisting and new calls are cancelled.
   *
   * This method is only valid for clients that use an internal channel. If the client was created
   * with a shared, user-provided channel, the channel itself should be closed.
   *
   * @throws akka.grpc.GrpcClientCloseException if client was created with a user-provided [[akka.grpc.GrpcChannel]].
   */
  def close(): CompletionStage[Done]
  def closed(): CompletionStage[Done]
}
