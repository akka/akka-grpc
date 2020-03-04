/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.net.URI

import akka.discovery.ServiceDiscovery
import io.grpc.{ NameResolver, NameResolverProvider }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class AkkaDiscoveryNameResolverProvider(
    discovery: ServiceDiscovery,
    defaultPort: Int,
    portName: Option[String],
    protocol: Option[String],
    resolveTimeout: FiniteDuration)(implicit ec: ExecutionContext)
    extends NameResolverProvider {
  override def isAvailable: Boolean = true

  override def priority(): Int = 5

  override def getDefaultScheme: String = "http"

  override def newNameResolver(targetUri: URI, args: NameResolver.Args): AkkaDiscoveryNameResolver = {
    require(targetUri.getAuthority != null, s"target uri should not have null authority, got [$targetUri]")
    new AkkaDiscoveryNameResolver(discovery, defaultPort, targetUri.getAuthority, portName, protocol, resolveTimeout)
  }
}
