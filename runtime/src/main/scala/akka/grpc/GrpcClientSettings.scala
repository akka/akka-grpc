/**
 * Copyright (C) 2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc

import akka.actor.ActorSystem
import com.typesafe.config.Config

import scala.concurrent.duration.Duration
import io.grpc.CallCredentials

object GrpcClientSettings {
  /** Scala API */
  def apply(host: String, port: Int): GrpcClientSettings =
    new GrpcClientSettings(host, port)

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
  def apply(config: Config): GrpcClientSettings = {
    var result = GrpcClientSettings(config getString "host", config getInt "port")
      .withDeadline(getPotentiallyInfiniteDuration(config, "deadline"))

    if (config.hasPath("override-authority"))
      result = result.withOverrideAuthority(config.getString("override-authority"))
    if (config.hasPath("trusted-ca-certificate"))
      result = result.withTrustedCaCertificate(config.getString("trusted-ca-certificate"))
    if (config.hasPath("user-agent"))
      result = result.withUserAgent(config.getString("user-agent"))

    result
  }

  private def getPotentiallyInfiniteDuration(underlying: Config, path: String): Duration = underlying.getString(path) match {
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
  val host: String,
  val port: Int,
  val callCredentials: Option[CallCredentials] = None,
  val overrideAuthority: Option[String] = None,
  val trustedCaCertificate: Option[String] = None,
  val deadline: Duration = Duration.Undefined,
  val userAgent: Option[String] = None) {

  def withHost(value: String): GrpcClientSettings = copy(host = value)
  def withPort(value: Int): GrpcClientSettings = copy(port = value)
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
    host: String = host,
    port: Int = port,
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
