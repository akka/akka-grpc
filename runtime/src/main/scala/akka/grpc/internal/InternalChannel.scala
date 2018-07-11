/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.Done
import akka.annotation.InternalApi
import io.grpc.ManagedChannel

import scala.concurrent.{Future, Promise}
import scala.compat.java8.FutureConverters._

@InternalApi
/**
  * INTERNAL API
  * Used from generated code so can't be private.
  */
class InternalChannel(val managedChannel: Future[ManagedChannel], promiseDone: Promise[Done]) {

  val doneCS = promiseDone.future.toJava
  val done = promiseDone.future

}
