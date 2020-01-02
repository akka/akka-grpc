/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
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
 */
@InternalApi
case class InternalChannel(managedChannel: ManagedChannel, done: Future[Done])
