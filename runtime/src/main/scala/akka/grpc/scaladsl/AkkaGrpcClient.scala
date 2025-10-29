/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import scala.concurrent.Future

import akka.Done
import akka.annotation.DoNotInherit

/** Common trait of all generated Akka gRPC clients. Not for user extension. */
@DoNotInherit
trait AkkaGrpcClient {

  /**
   * Initiates a shutdown in which preexisting and new calls are cancelled.
   *
   * This method is only valid for clients that use an internal channel. If the client was created
   * with a shared user-provided channel, the channel itself should be closed.
   *
   * @throws akka.grpc.GrpcClientCloseException if client was created with a user-provided [[akka.grpc.GrpcChannel]].
   */
  def close(): Future[Done]

  /**
   * A Future that completes successfully when shutdown via close()
   * or exceptionally if a connection can not be established or reestablished
   * after maxConnectionAttempts.
   */
  def closed: Future[Done]

  /**
   * The same client instance decorated to add the given key and value to the metadata of any request issued.
   */
  def addRequestHeader(key: String, value: String): AkkaGrpcClient = {
    // dummy implementation to not break compatibility
    this
  }
}
