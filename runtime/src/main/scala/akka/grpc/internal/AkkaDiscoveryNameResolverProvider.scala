/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ActorSystem
import akka.annotation.InternalApi

import java.net.URI
import akka.discovery.ServiceDiscovery
import io.grpc.{ NameResolver, NameResolverProvider }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class AkkaDiscoveryNameResolverProvider(
    discovery: ServiceDiscovery,
    defaultPort: Int,
    serviceName: String,
    portName: Option[String],
    protocol: Option[String],
    resolveTimeout: FiniteDuration,
    refreshInterval: Option[FiniteDuration])(implicit ec: ExecutionContext, system: ActorSystem)
    extends NameResolverProvider {
  override def isAvailable: Boolean = true

  override def priority(): Int = 5

  override def getDefaultScheme: String = "http"

  override def newNameResolver(targetUri: URI, args: NameResolver.Args): AkkaDiscoveryNameResolver = {
    new AkkaDiscoveryNameResolver(
      discovery,
      defaultPort,
      serviceName,
      portName,
      protocol,
      resolveTimeout,
      refreshInterval)
  }
}
