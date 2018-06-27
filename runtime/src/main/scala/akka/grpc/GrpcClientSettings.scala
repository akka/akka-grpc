/**
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import com.typesafe.config.Config
import io.grpc.CallCredentials

import scala.collection.immutable
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.grpc.internal.HardcodedServiceDiscovery
import akka.util.Helpers

object GrpcClientSettings {
  val DefaultResolveTimeout = 5.seconds
  val DefaultPort = 443

  /** Scala API */
  def apply(host: String, port: Int): GrpcClientSettings =
    new GrpcClientSettings(host, hardcodedServiceDiscovery(host, port))

  /** Scala API */
  def apply(name: String, serviceDiscovery: SimpleServiceDiscovery) =
    new GrpcClientSettings(name, serviceDiscovery)

  /** Scala API */
  def apply(serviceName: String, sys: ActorSystem): GrpcClientSettings = {
    val akkaGrpcClientConfig = sys.settings.config.getConfig("akka.grpc.client")

    val serviceConfig =
      if (akkaGrpcClientConfig.hasPath('"' + serviceName + '"'))
        akkaGrpcClientConfig.getConfig('"' + serviceName + '"').withFallback(akkaGrpcClientConfig.getConfig("\"*\""))
      else
        akkaGrpcClientConfig.getConfig("\"*\"")

    GrpcClientSettings(serviceConfig)
  }

  /** Scala API */
  def apply(config: Config): GrpcClientSettings = {
    GrpcClientSettings(config getString "host", config getInt "port")
      .copy(
        overrideAuthority = getOptionalString(config, "override-authority"),
        deadline = getPotentiallyInfiniteDuration(config, "deadline"),
        trustedCaCertificate = getOptionalString(config, "trusted-ca-certificate"),
        userAgent = getOptionalString(config, "user-agent"))
  }

  private def getOptionalString(config: Config, path: String): Option[String] = config.getString(path) match {
    case "" => None
    case other => Some(other)
  }

  private def getPotentiallyInfiniteDuration(underlying: Config, path: String): Duration = Helpers.toRootLowerCase(underlying.getString(path)) match {
    case "infinite" ⇒ Duration.Inf
    case _ ⇒ Duration.fromNanos(underlying.getDuration(path).toNanos)
  }

  /** Java API */
  def create(serviceName: String, sys: ActorSystem): GrpcClientSettings =
    GrpcClientSettings(serviceName, sys)

  /** Java API */
  def create(config: Config): GrpcClientSettings =
    GrpcClientSettings(config)

  /** Java API */
  def create(host: String, port: Int): GrpcClientSettings = apply(host, port)

  /** Java API */
  def create(name: String, serviceDiscovery: SimpleServiceDiscovery) =
    new GrpcClientSettings(name, serviceDiscovery)

  private def hardcodedServiceDiscovery(host: String, port: Int) = new HardcodedServiceDiscovery(Resolved(host, immutable.Seq(ResolvedTarget(host, Some(port)))))
}

final class GrpcClientSettings private (
  val name: String,
  val serviceDiscovery: SimpleServiceDiscovery,
  val defaultPort: Int = 443,
  val callCredentials: Option[CallCredentials] = None,
  val overrideAuthority: Option[String] = None,
  val trustedCaCertificate: Option[String] = None,
  val deadline: Duration = Duration.Undefined,
  val userAgent: Option[String] = None,
  val useTls: Boolean = true,
  val resolveTimeout: FiniteDuration = 1.second) {

  def withName(value: String): GrpcClientSettings = copy(name = value)
  /**
   * If using ServiceDiscovery and no port is returned use this one.
   */
  def withDefaultPort(value: Int): GrpcClientSettings = copy(defaultPort = value)
  def withCallCredentials(value: CallCredentials): GrpcClientSettings = copy(callCredentials = Option(value))
  def withOverrideAuthority(value: String): GrpcClientSettings = copy(overrideAuthority = Option(value))
  def withTrustedCaCertificate(value: String): GrpcClientSettings = copy(trustedCaCertificate = Option(value))

  def withResolveTimeout(value: FiniteDuration): GrpcClientSettings = copy(resolveTimeout = value)
  /**
   * Each call will have this deadline.
   */
  def withDeadline(value: Duration): GrpcClientSettings = copy(deadline = value)
  /**
   * Each call will have this deadline.
   */
  def withDeadline(value: java.time.Duration): GrpcClientSettings = copy(deadline = Duration.fromNanos(value.toNanos))
  /**
   * Provides a custom `User-Agent` for the application.
   *
   * It's an optional parameter. The library will provide a user agent independent of this
   * option. If provided, the given agent will prepend the library's user agent information.
   */
  def withUserAgent(value: String): GrpcClientSettings = copy(userAgent = Option(value))

  /**
   * Set to false to use unencrypted HTTP/2. This should not be used in production system.
   */
  def withTls(enabled: Boolean): GrpcClientSettings =
    copy(useTls = enabled)

  private def copy(
    name: String = name,
    defaultPort: Int = defaultPort,
    callCredentials: Option[CallCredentials] = callCredentials,
    overrideAuthority: Option[String] = overrideAuthority,
    trustedCaCertificate: Option[String] = trustedCaCertificate,
    deadline: Duration = deadline,
    userAgent: Option[String] = userAgent,
    useTls: Boolean = useTls,
    resolveTimeout: FiniteDuration = resolveTimeout,
  ): GrpcClientSettings = new GrpcClientSettings(
    callCredentials = callCredentials,
    serviceDiscovery = serviceDiscovery,
    deadline = deadline,
    name = name,
    overrideAuthority = overrideAuthority,
    defaultPort = defaultPort,
    trustedCaCertificate = trustedCaCertificate,
    userAgent = userAgent,
    useTls = useTls,
    resolveTimeout = resolveTimeout,
  )

}
