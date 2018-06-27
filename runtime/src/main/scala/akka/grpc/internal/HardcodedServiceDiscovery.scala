/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.Resolved

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class HardcodedServiceDiscovery(resolved: Resolved) extends SimpleServiceDiscovery {
  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] = {
    Future.successful(resolved)
  }
}
