/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
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
   */
  def close(): Future[Done]

  /**
   * Returns a Future that completes successfully when shutdown via close()
   * or exceptionally if a connection can not be established or reestablished
   * after maxConnectionAttempts.
   */
  def closed(): Future[Done]
}
