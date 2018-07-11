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
import akka.util.JavaDurationConverters._

object GrpcClientSettings {

  /** Scala API */
  def apply(host: String, port: Int): GrpcClientSettings =
    // hardcoded doesn't use the resolve timeout
    new GrpcClientSettings(host, hardcodedServiceDiscovery(host, port), port, 1.second)

  /**
   * Scala API
   *
   * @param serviceName Name to look up in serviceDiscovery
   * @param defaultPort Port to use if service discovery only returns a host name
   * @param resolveTimeout passed to calls to resolve on ServiceDiscovery
   */
  def apply(serviceName: String, defaultPort: Int, serviceDiscovery: SimpleServiceDiscovery, resolveTimeout: FiniteDuration) =
    new GrpcClientSettings(serviceName, serviceDiscovery, defaultPort, resolveTimeout)

  /**
   * Scala API
   *
   * @param serviceName of the service to lookup config from the ActorSystem's config
   */
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
    GrpcClientSettings(config.getString("host"), config.getInt("port"))
      .copy(
        overrideAuthority = getOptionalString(config, "override-authority"),
        deadline = getPotentiallyInfiniteDuration(config, "deadline"),
        trustedCaCertificate = getOptionalString(config, "trusted-ca-certificate"),
        userAgent = getOptionalString(config, "user-agent"),
        connectionAttempts = config.getInt("connection-attempts"))
  }

  private def getOptionalString(config: Config, path: String): Option[String] = config.getString(path) match {
    case "" => None
    case other => Some(other)
  }

  private def getPotentiallyInfiniteDuration(underlying: Config, path: String): Duration = Helpers.toRootLowerCase(underlying.getString(path)) match {
    case "infinite" ⇒ Duration.Inf
    case _ ⇒ Duration.fromNanos(underlying.getDuration(path).toNanos)
  }

  /**
   * Java API
   *
   * @param serviceName Name of the service to look up in the ActorSystem's config
   */
  def create(serviceName: String, sys: ActorSystem): GrpcClientSettings =
    GrpcClientSettings(serviceName, sys)

  /**
   * Java API
   */
  def create(config: Config): GrpcClientSettings =
    GrpcClientSettings(config)

  /** Java API */
  def create(host: String, port: Int): GrpcClientSettings = apply(host, port)

  /**
   * Java API
   *
   * @param serviceName Name of the service to look up in the ServiceDiscovery
   * @param defaultPort Port to use if service discovery only return a host
   * @param resolveTimeout Passed to service discovery resolve
   */
  def create(serviceName: String, defaultPort: Int, serviceDiscovery: SimpleServiceDiscovery, resolveTimeout: java.time.Duration) =
    apply(serviceName, defaultPort, serviceDiscovery, resolveTimeout.asScala)

  private def hardcodedServiceDiscovery(host: String, port: Int) = new HardcodedServiceDiscovery(Resolved(host, immutable.Seq(ResolvedTarget(host, Some(port)))))
}

final class GrpcClientSettings private (
  val name: String,
  val serviceDiscovery: SimpleServiceDiscovery,
  val defaultPort: Int,
  val resolveTimeout: FiniteDuration,
  val connectionAttempts: Int = 5,
  val callCredentials: Option[CallCredentials] = None,
  val overrideAuthority: Option[String] = None,
  val trustedCaCertificate: Option[String] = None,
  val deadline: Duration = Duration.Undefined,
  val userAgent: Option[String] = None,
  val useTls: Boolean = true) {

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

  /**
   * How many times to retry establishing a connection before failing the client
   * Failure can be monitored using client.stopped and monitoring the Future/CompletionStage.
   * An exponentially increasing backoff is used between attempts.
   */
  def withConnectionAttempts(value: Int): GrpcClientSettings =
    copy(connectionAttempts = value)

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
    connectionAttempts: Int = connectionAttempts): GrpcClientSettings = new GrpcClientSettings(
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
    connectionAttempts = connectionAttempts)

}
