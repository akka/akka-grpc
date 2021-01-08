/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletionStage

import akka.Done
import akka.annotation.DoNotInherit

/** Common trait of all generated Akka gRPC clients. Not for user extension. */
@DoNotInherit
trait AkkaGrpcClient {
  def close(): CompletionStage[Done]
  def closed(): CompletionStage[Done]
}
