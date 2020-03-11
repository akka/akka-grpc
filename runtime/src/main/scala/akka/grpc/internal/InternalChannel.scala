/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.Future

import akka.Done
import akka.annotation.InternalApi

import io.grpc.ManagedChannel

/**
 * INTERNAL API
 */
@InternalApi
case class InternalChannel(managedChannel: ManagedChannel, done: Future[Done])
