/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage

import akka.Done
import akka.annotation.InternalApi
import io.grpc.ManagedChannel

import scala.concurrent.{ Future, Promise }
import scala.compat.java8.FutureConverters._

/**
 * INTERNAL API
 * Used from generated code so can't be private.
 */
@InternalApi
class InternalChannel(val managedChannel: Future[ManagedChannel], promiseDone: Promise[Done]) {
  val doneCS: CompletionStage[Done] = promiseDone.future.toJava
  val done: Future[Done] = promiseDone.future
}
