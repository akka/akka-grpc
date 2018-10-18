/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
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
   */
  def close(): CompletionStage[Done]

  /**
   * Returns a CompletionStage that completes successfully when shutdown via close()
   * or exceptionally if a connection can not be established or reestablished
   * after maxConnectionAttempts.
   */
  def closed(): CompletionStage[Done]
}
