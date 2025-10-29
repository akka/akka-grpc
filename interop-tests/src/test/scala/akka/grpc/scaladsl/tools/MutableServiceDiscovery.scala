/*
 * Copyright (C) 2020-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl.tools

import java.net.InetSocketAddress

import akka.discovery.Lookup
import akka.discovery.ServiceDiscovery
import akka.discovery.ServiceDiscovery.Resolved
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.http.scaladsl.Http

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * An In-Memory ServiceDiscovery that only can lookup "greeter"
 */
final class MutableServiceDiscovery(targets: List[InetSocketAddress]) extends ServiceDiscovery {
  var services: Future[Resolved] = _

  setServices(targets)

  def setServices(targets: List[InetSocketAddress]): Unit =
    services = Future.successful(
      Resolved(
        "greeter",
        targets.map(target => ResolvedTarget(target.getHostString, Some(target.getPort), Option(target.getAddress)))))

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    require(query.serviceName == "greeter")
    services
  }
}

object MutableServiceDiscovery {
  def apply(targets: List[Http.ServerBinding]) = new MutableServiceDiscovery(targets.map(_.localAddress))
}
