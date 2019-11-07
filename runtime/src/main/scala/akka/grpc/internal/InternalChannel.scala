/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }

import akka.Done
import akka.annotation.InternalApi

import io.grpc.ManagedChannel

/**
 * INTERNAL API
 * Used from generated code so can't be private.
 */
@InternalApi
class InternalChannel(channel: Future[(ManagedChannel, Future[Done])]) {
  def managedChannel(implicit ec: ExecutionContext): Future[ManagedChannel] = channel.map(_._1)
  def done(implicit ec: ExecutionContext): Future[Done] = channel.flatMap { case (_, done) => done }
}
