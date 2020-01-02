/*
 * Copyright (C) 2019-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.discovery.Lookup
import akka.discovery.ServiceDiscovery
import akka.discovery.ServiceDiscovery.Resolved
import akka.pattern.AskTimeoutException

class AlwaysTimingOutDiscovery extends ServiceDiscovery {
  def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.failed(new AskTimeoutException("Simulated timeout"))
}
