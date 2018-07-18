/**
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.io.{IOException, InputStream}

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{Resolved, ResolvedTarget}
import akka.grpc.internal.{HardcodedServiceDiscovery, NettyClientUtils}
import akka.util.Helpers
import akka.util.JavaDurationConverters._
import com.typesafe.config.Config
import com.typesafe.sslconfig.ssl.{ConfigSSLContextBuilder, DefaultKeyManagerFactoryWrapper, DefaultTrustManagerFactoryWrapper, SSLConfigFactory, SSLConfigSettings}
import io.grpc.CallCredentials
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import javax.net.ssl.SSLContext

import scala.collection.immutable
import scala.concurrent.duration.{Duration, _}

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

    val serviceConfig = {
      // Use config named "*" by default
      val defaultServiceConfig = akkaGrpcClientConfig.getConfig("\"*\"")
      // Override "*" config with specific service config if available
      if (akkaGrpcClientConfig.hasPath('"' + serviceName + '"'))
        akkaGrpcClientConfig.getConfig('"' + serviceName + '"').withFallback(defaultServiceConfig)
      else
        defaultServiceConfig
    }

    GrpcClientSettings(serviceConfig)
  }

  /** Scala API */
  def apply(config: Config): GrpcClientSettings = {
    var settings = GrpcClientSettings(config getString "host", config getInt "port")
      .copy(
        overrideAuthority = getOptionalString(config, "override-authority"),
        deadline = getPotentiallyInfiniteDuration(config, "deadline"),
        userAgent = getOptionalString(config, "user-agent"),
        sslContext = getOptionalSSLContext(config, "ssl-config")
      )

    // FIXME: Only including this to catch code that I've failed to upgrade
    getOptionalString(config, "trusted-ca-certificate").foreach(_ => ???)

    settings
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

  /**
   * INTERNAL API
   *
   * @param config The config to parse, assumes already at the right path.
   */
  @InternalApi
  private def getSSLContext(config: Config): SSLContext = {
    val sslConfigSettings: SSLConfigSettings = SSLConfigFactory.parse(config)
    val sslContext: SSLContext = new ConfigSSLContextBuilder(
      com.typesafe.sslconfig.util.PrintlnLogger.factory, // FIXME
      sslConfigSettings,
      new DefaultKeyManagerFactoryWrapper(sslConfigSettings.keyManagerConfig.algorithm),
      new DefaultTrustManagerFactoryWrapper(sslConfigSettings.trustManagerConfig.algorithm)
    ).build()
    sslContext
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private def getOptionalSSLContext(config: Config, path: String): Option[SSLContext] = {
    if (config.hasPath(path))
      Some(getSSLContext(config.getConfig(path)))
    else
      None
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  def sslContextForCert(certPath: String): SSLContext = { // FIXME: Remove once unused
    // Use Netty's SslContextBuilder internally to help us construct a SSLContext
    val fullCertPath = "/certs/" + certPath
    val certStream: InputStream = getClass.getResourceAsStream(fullCertPath)
    if (certStream == null) throw new IOException(s"Couldn't find '$fullCertPath' on the classpath")
    val sslBuilder = try { GrpcSslContexts.forClient.trustManager(certStream) } finally certStream.close()
    NettyClientUtils.buildJdkSslContext(sslBuilder).context
  }

}

final class GrpcClientSettings private (
  val name: String,
  val serviceDiscovery: SimpleServiceDiscovery,
  val defaultPort: Int,
  val resolveTimeout: FiniteDuration,
  val callCredentials: Option[CallCredentials] = None,
  val overrideAuthority: Option[String] = None,
  val sslContext: Option[SSLContext] = None,
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
  def withSSLContext(context: SSLContext) = copy(sslContext = Option(context))
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
    sslContext: Option[SSLContext] = sslContext,
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
    sslContext = sslContext,
    userAgent = userAgent,
    useTls = useTls,
    resolveTimeout = resolveTimeout,
  )

}
