/**
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.actor.ActorSystem
import akka.util.Helpers
import com.typesafe.config.Config

import scala.concurrent.duration.Duration
import io.grpc.CallCredentials

object GrpcClientSettings {
  /** Scala API */
  def apply(host: String, port: Int): GrpcClientSettings =
    new GrpcClientSettings(Some(host), Some(port))

  /**
   * Apply default settings from config. You will need to provide a host and port for the client
   * to be able to actually use the returned settings.
   *
   * Scala API
   */
  def apply(sys: ActorSystem): GrpcClientSettings = {
    val akkaGrpcClientConfig = sys.settings.config.getConfig("akka.grpc.client")
    GrpcClientSettings(akkaGrpcClientConfig.getConfig("\"*\""))
  }

  /**
   * Apply specific settings from system for `serviceName` falling back to the '*' settings for defaults.
   * If the specified `serviceName` does not have a host and port in the config it will have to be provided
   * programmatically.
   *
   * Scala API
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
    val host = getOptionalString(config, "host")

    val port =
      if (config.hasPath("port")) Some(config.getInt("port"))
      else None

    new GrpcClientSettings(host, port)
      .copy(
        overrideAuthority = getOptionalString(config, "override-authority"),
        deadline = getPotentiallyInfiniteDuration(config, "deadline"),
        trustedCaCertificate = getOptionalString(config, "trusted-ca-certificate"),
        userAgent = getOptionalString(config, "user-agent"))
  }

  private def getOptionalString(config: Config, path: String): Option[String] =
    if (config.hasPath(path))
      config.getString(path) match {
        case "" => None
        case other => Some(other)
      }
    else None

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
}

final class GrpcClientSettings private (
  val host: Option[String],
  val port: Option[Int],
  val callCredentials: Option[CallCredentials] = None,
  val overrideAuthority: Option[String] = None,
  val trustedCaCertificate: Option[String] = None,
  val deadline: Duration = Duration.Undefined,
  val userAgent: Option[String] = None) {

  def withHost(value: String): GrpcClientSettings = copy(host = Some(value))
  def withPort(value: Int): GrpcClientSettings = copy(port = Some(value))
  def withCallCredentials(value: CallCredentials): GrpcClientSettings = copy(callCredentials = Option(value))
  def withOverrideAuthority(value: String): GrpcClientSettings = copy(overrideAuthority = Option(value))
  def withTrustedCaCertificate(value: String): GrpcClientSettings = copy(trustedCaCertificate = Option(value))
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

  private def copy(
    host: Option[String] = host,
    port: Option[Int] = port,
    callCredentials: Option[CallCredentials] = callCredentials,
    overrideAuthority: Option[String] = overrideAuthority,
    trustedCaCertificate: Option[String] = trustedCaCertificate,
    deadline: Duration = deadline,
    userAgent: Option[String] = userAgent): GrpcClientSettings = new GrpcClientSettings(
    callCredentials = callCredentials,
    deadline = deadline,
    host = host,
    overrideAuthority = overrideAuthority,
    port = port,
    trustedCaCertificate = trustedCaCertificate,
    userAgent = userAgent)

}
