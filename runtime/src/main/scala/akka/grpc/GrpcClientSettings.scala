/**
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.discovery.{ ServiceDiscovery, SimpleServiceDiscovery }
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.grpc.internal.HardcodedServiceDiscovery
import akka.util.Helpers
import akka.util.JavaDurationConverters._
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.sslconfig.ssl.{ ConfigSSLContextBuilder, DefaultKeyManagerFactoryWrapper, DefaultTrustManagerFactoryWrapper, SSLConfigFactory, SSLConfigSettings }
import io.grpc.CallCredentials
import javax.net.ssl.SSLContext

import scala.collection.immutable
import scala.concurrent.duration.{ Duration, _ }

object GrpcClientSettings {

  /**
   * Scala API
   *
   * Create a client hat uses a static host and port. Default configuration
   * is loaded from reference.conf
   */
  def apply(host: String, port: Int)(implicit actorSystem: ActorSystem): GrpcClientSettings = {
    // default is hardcoded
    val defaultServiceConfig = actorSystem.settings.config.getConfig("akka.grpc.client").getConfig("\"*\"")
      .withValue("service-name", ConfigValueFactory.fromAnyRef(host))
      .withValue("port", ConfigValueFactory.fromAnyRef(port))
    GrpcClientSettings(defaultServiceConfig)
  }

  /**
   * Scala API
   *
   * Create a client with the given service discovery mechanism. Default configuration
   * is loaded from reference.conf
   *
   * @param serviceName               Name to look up in serviceDiscovery
   * @param defaultPort               Port to use if service discovery only returns a host name
   * @param serviceDiscoveryMechanism Service discovery mechanism to use. Must be correctly configured in the ActorSystem's config
   */
  def apply(serviceName: String, defaultPort: Int, serviceDiscoveryMechanism: String)(implicit actorSystem: ActorSystem): GrpcClientSettings = {
    val defaultServiceConfig = actorSystem.settings.config.getConfig("akka.grpc.client").getConfig("\"*\"")
      .withValue("service-name", ConfigValueFactory.fromAnyRef(serviceName))
      .withValue("port", ConfigValueFactory.fromAnyRef(defaultPort))
      .withValue("service-discovery-mechanism", ConfigValueFactory.fromAnyRef(serviceDiscoveryMechanism))
    GrpcClientSettings(defaultServiceConfig)
  }

  /**
   * Scala API
   *
   * Look up client settings from an ActorSystem's configuration. Client configuration
   * must be under `akka.grpc.client`. Each client configuration falls back to the
   * defaults defined in reference.conf
   *
   * @param clientName of the client configuration to lookup config from the ActorSystem's config
   */
  def apply(clientName: String)(implicit actorSystem: ActorSystem): GrpcClientSettings = {
    val akkaGrpcClientConfig = actorSystem.settings.config.getConfig("akka.grpc.client")
    val clientConfig = {
      // Use config named "*" by default
      val defaultServiceConfig = akkaGrpcClientConfig.getConfig("\"*\"")
      require(akkaGrpcClientConfig.hasPath('"' + clientName + '"'), s"Config path `akka.grpc.client.$clientName` does not exist")
      akkaGrpcClientConfig.getConfig('"' + clientName + '"').withFallback(defaultServiceConfig)
    }

    GrpcClientSettings(clientConfig)
  }

  /**
   * Scala API
   *
   * Configure client via the provided Config. See reference.conf for configuration properties.
   */
  def apply(clientConfiguration: Config)(implicit sys: ActorSystem): GrpcClientSettings = {
    val serviceDiscoveryMechanism = clientConfiguration.getString("service-discovery-mechanism")
    val serviceName = clientConfiguration.getString("service-name")
    val port = clientConfiguration.getInt("port")
    val resolveTimeout = clientConfiguration.getDuration("resolve-timeout").asScala
    val sd = serviceDiscoveryMechanism match {
      case "hardcoded" =>
        hardcodedServiceDiscovery(serviceName, port)
      case other =>
        ServiceDiscovery(sys).loadServiceDiscovery(other)
    }
    new GrpcClientSettings(serviceName, sd, port, resolveTimeout)
      .copy(
        overrideAuthority = getOptionalString(clientConfiguration, "override-authority"),
        deadline = getPotentiallyInfiniteDuration(clientConfiguration, "deadline"),
        userAgent = getOptionalString(clientConfiguration, "user-agent"),
        sslContext = getOptionalSSLContext(clientConfiguration, "ssl-config"),
        connectionAttempts = getOptionalInt(clientConfiguration, "connection-attempts"))
  }

  private def getOptionalString(config: Config, path: String): Option[String] = config.getString(path) match {
    case "" => None
    case other => Some(other)
  }

  private def getOptionalInt(config: Config, path: String): Option[Int] = config.getInt(path) match {
    case -1 => None // retry forever
    case other => Some(other)
  }

  private def getPotentiallyInfiniteDuration(underlying: Config, path: String): Duration = Helpers.toRootLowerCase(underlying.getString(path)) match {
    case "infinite" ⇒ Duration.Inf
    case _ ⇒ Duration.fromNanos(underlying.getDuration(path).toNanos)
  }

  /**
   * Java API
   *
   * Look up client settings from an ActorSystem's configuration. Client configuration
   * must be under `akka.grpc.client`. Each client configuration falls back to the
   * defaults defined in reference.conf
   *
   * @param clientName Name of the client to look up in the ActorSystem's config
   */
  def create(clientName: String, sys: ActorSystem): GrpcClientSettings =
    apply(clientName)(sys)

  /**
   * Java API
   *
   * Configure client via the provided Config. See reference.conf for configuration properties.
   */
  def create(config: Config, sys: ActorSystem): GrpcClientSettings = apply(config)(sys)

  /** Java API */
  def create(host: String, port: Int, actorSystem: ActorSystem): GrpcClientSettings = apply(host, port)(actorSystem)

  /**
   * Java API
   *
   * @param serviceName    Name of the service to look up in the ServiceDiscovery
   * @param defaultPort    Port to use if service discovery only return a host
   * @param serviceDiscoveryMechanism Service discovery mechanism to use. Must be correctly configured in the ActorSystem's config
   */
  def create(serviceName: String, defaultPort: Int, serviceDiscoveryMechanism: String, actorSystem: ActorSystem) =
    apply(serviceName, defaultPort, serviceDiscoveryMechanism)(actorSystem)

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
      com.typesafe.sslconfig.util.NoopLogger.factory, // FIXME
      sslConfigSettings,
      new DefaultKeyManagerFactoryWrapper(sslConfigSettings.keyManagerConfig.algorithm),
      new DefaultTrustManagerFactoryWrapper(sslConfigSettings.trustManagerConfig.algorithm)).build()
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

}

/**
 * @param serviceName      Service name to lookup in serviceDiscovery.
 * @param serviceDiscovery Service discovery mechanism to use. For cases where there isn't an ActorSystem this is a hardcoded version.
 */
final class GrpcClientSettings private (
  val serviceName: String,
  val serviceDiscovery: SimpleServiceDiscovery,
  val defaultPort: Int,
  val resolveTimeout: FiniteDuration,
  val connectionAttempts: Option[Int] = None,
  val callCredentials: Option[CallCredentials] = None,
  val overrideAuthority: Option[String] = None,
  val sslContext: Option[SSLContext] = None,
  val deadline: Duration = Duration.Undefined,
  val userAgent: Option[String] = None,
  val useTls: Boolean = true) {

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

  /**
   * How many times to retry establishing a connection before failing the client
   * Failure can be monitored using client.stopped and monitoring the Future/CompletionStage.
   * An exponentially increasing backoff is used between attempts.
   */
  def withConnectionAttempts(value: Int): GrpcClientSettings =
    copy(connectionAttempts = Some(value))

  private def copy(
    serviceName: String = serviceName,
    defaultPort: Int = defaultPort,
    callCredentials: Option[CallCredentials] = callCredentials,
    overrideAuthority: Option[String] = overrideAuthority,
    sslContext: Option[SSLContext] = sslContext,
    deadline: Duration = deadline,
    userAgent: Option[String] = userAgent,
    useTls: Boolean = useTls,
    resolveTimeout: FiniteDuration = resolveTimeout,
    connectionAttempts: Option[Int] = connectionAttempts): GrpcClientSettings = new GrpcClientSettings(
    callCredentials = callCredentials,
    serviceDiscovery = serviceDiscovery,
    deadline = deadline,
    serviceName = serviceName,
    overrideAuthority = overrideAuthority,
    defaultPort = defaultPort,
    sslContext = sslContext,
    userAgent = userAgent,
    useTls = useTls,
    resolveTimeout = resolveTimeout,
    connectionAttempts = connectionAttempts)

}
