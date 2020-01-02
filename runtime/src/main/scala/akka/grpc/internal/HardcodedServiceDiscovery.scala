/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.discovery.ServiceDiscovery.Resolved

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class HardcodedServiceDiscovery(resolved: Resolved) extends ServiceDiscovery {
  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.successful(resolved)
}
