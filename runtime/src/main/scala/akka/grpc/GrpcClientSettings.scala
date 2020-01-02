/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.actor.ActorSystem
import akka.annotation.{ ApiMayChange, InternalApi }
import akka.discovery.{ Discovery, ServiceDiscovery }
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.grpc.internal.HardcodedServiceDiscovery
import akka.util.Helpers
import akka.util.JavaDurationConverters._
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.sslconfig.akka.util.AkkaLoggerFactory
import com.typesafe.sslconfig.ssl.{
  ConfigSSLContextBuilder,
  DefaultKeyManagerFactoryWrapper,
  DefaultTrustManagerFactoryWrapper,
  SSLConfigFactory,
  SSLConfigSettings
}
import io.grpc.CallCredentials
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import javax.net.ssl.SSLContext
import scala.collection.immutable
import scala.concurrent.duration.{ Duration, _ }

object GrpcClientSettings {

  /**
   * Create a client that uses a static host and port. Default configuration
   * is loaded from reference.conf
   */
  def connectToServiceAt(host: String, port: Int)(implicit actorSystem: ActorSystem): GrpcClientSettings = {
    // default is static
    val defaultServiceConfig = actorSystem.settings.config
      .getConfig("akka.grpc.client")
      .getConfig("\"*\"")
      .withValue("host", ConfigValueFactory.fromAnyRef(host))
      .withValue("port", ConfigValueFactory.fromAnyRef(port))
    GrpcClientSettings.fromConfig(defaultServiceConfig)
  }

  /**
   * Look up client settings from an ActorSystem's configuration. Client configuration
   * must be under `akka.grpc.client`. Each client configuration falls back to the
   * defaults defined in reference.conf
   *
   * @param clientName of the client configuration to lookup config from the ActorSystem's config
   */
  def fromConfig(clientName: String)(implicit actorSystem: ActorSystem): GrpcClientSettings = {
    val akkaGrpcClientConfig = actorSystem.settings.config.getConfig("akka.grpc.client")
    val clientConfig = {
      // Use config named "*" by default
      val defaultServiceConfig = akkaGrpcClientConfig.getConfig("\"*\"")
      require(
        akkaGrpcClientConfig.hasPath('"' + clientName + '"'),
        s"Config path `akka.grpc.client.$clientName` does not exist")
      akkaGrpcClientConfig.getConfig('"' + clientName + '"').withFallback(defaultServiceConfig)
    }

    GrpcClientSettings.fromConfig(clientConfig)
  }

  /**
   * Configure the client to lookup a valid hostname:port from a service registry accessed via the `ServiceDiscovery`
   * instance registered in the `actorSystem` provided. When invoking a lookup operation on the service registry, a
   * name is required and optionally a port name and a protocol. This factory method only requires a `serviceName`.
   * Use `withServicePortName` and `withServiceProtocol` to refine the lookup on the service registry.
   *
   * @param serviceName name of the remote service to lookup.
   */
  def usingServiceDiscovery(serviceName: String)(implicit actorSystem: ActorSystem): GrpcClientSettings = {
    val clientConfiguration: Config = actorSystem.settings.config.getConfig("akka.grpc.client").getConfig("\"*\"")
    val resolveTimeout = clientConfiguration.getDuration("service-discovery.resolve-timeout").asScala
    val discovery = Discovery.get(actorSystem).discovery
    withConfigDefaults(serviceName, discovery, -1, resolveTimeout, clientConfiguration)
  }

  /**
   * Configure client via the provided Config. See reference.conf for configuration properties.
   */
  def fromConfig(clientConfiguration: Config)(implicit sys: ActorSystem): GrpcClientSettings = {
    val serviceDiscoveryMechanism = clientConfiguration.getString("service-discovery.mechanism")
    var serviceName = clientConfiguration.getString("service-discovery.service-name")
    val port = clientConfiguration.getInt("port")
    val resolveTimeout = clientConfiguration.getDuration("service-discovery.resolve-timeout").asScala
    val sd = serviceDiscoveryMechanism match {
      case "static" | "grpc-dns" =>
        val host = clientConfiguration.getString("host")
        require(host.nonEmpty, "host can't be empty when service-discovery-mechanism is set to static or grpc-dns")
        // Required by the Discovery infrastructure, even when we use static discovery.
        if (serviceName.isEmpty)
          serviceName = "static"
        staticServiceDiscovery(host, port)
      case other =>
        require(serviceName.nonEmpty, "Configuration must contain a service-name")
        Discovery(sys).loadServiceDiscovery(other)
    }
    withConfigDefaults(serviceName, sd, port, resolveTimeout, clientConfiguration)
  }

  /**
   * Given a base GrpcClientSettings, it generates a new instance with all values provided in config.
   */
  private def withConfigDefaults(
      serviceName: String,
      serviceDiscovery: ServiceDiscovery,
      defaultPort: Int,
      resolveTimeout: FiniteDuration,
      clientConfiguration: Config)(implicit actorSystem: ActorSystem): GrpcClientSettings =
    new GrpcClientSettings(
      serviceName,
      serviceDiscovery,
      defaultPort,
      resolveTimeout,
      getOptionalString(clientConfiguration, "service-discovery.port-name"),
      getOptionalString(clientConfiguration, "service-discovery.protocol"),
      clientConfiguration.getInt("creation.attempts"),
      clientConfiguration.getDuration("creation.delay").asScala,
      getOptionalInt(clientConfiguration, "connection-attempts"),
      None,
      getOptionalString(clientConfiguration, "override-authority"),
      getOptionalSSLContext(clientConfiguration, "ssl-config"),
      getPotentiallyInfiniteDuration(clientConfiguration, "deadline"),
      getOptionalString(clientConfiguration, "user-agent"),
      clientConfiguration.getBoolean("use-tls"),
      getOptionalString(clientConfiguration, "grpc-load-balancing"))

  private def getOptionalString(config: Config, path: String): Option[String] = config.getString(path) match {
    case ""    => None
    case other => Some(other)
  }

  private def getOptionalInt(config: Config, path: String): Option[Int] = config.getInt(path) match {
    case -1    => None // retry forever
    case other => Some(other)
  }

  private def getPotentiallyInfiniteDuration(underlying: Config, path: String): Duration =
    Helpers.toRootLowerCase(underlying.getString(path)) match {
      case "infinite" => Duration.Inf
      case _          => Duration.fromNanos(underlying.getDuration(path).toNanos)
    }

  private def staticServiceDiscovery(host: String, port: Int) =
    new HardcodedServiceDiscovery(Resolved(host, immutable.Seq(ResolvedTarget(host, Some(port), None))))

  /**
   * INTERNAL API
   *
   * @param config The config to parse, assumes already at the right path.
   */
  @InternalApi
  private def getSSLContext(config: Config)(implicit actorSystem: ActorSystem): SSLContext = {
    val sslConfigSettings: SSLConfigSettings = SSLConfigFactory.parse(config)
    val sslContext: SSLContext = new ConfigSSLContextBuilder(
      new AkkaLoggerFactory(actorSystem),
      sslConfigSettings,
      new DefaultKeyManagerFactoryWrapper(sslConfigSettings.keyManagerConfig.algorithm),
      new DefaultTrustManagerFactoryWrapper(sslConfigSettings.trustManagerConfig.algorithm)).build()
    sslContext
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private def getOptionalSSLContext(config: Config, path: String)(
      implicit actorSystem: ActorSystem): Option[SSLContext] =
    if (config.hasPath(path))
      Some(getSSLContext(config.getConfig(path)))
    else
      None
}

final class GrpcClientSettings private (
    val serviceName: String,
    val serviceDiscovery: ServiceDiscovery,
    val defaultPort: Int,
    val resolveTimeout: FiniteDuration,
    val servicePortName: Option[String] = None,
    val serviceProtocol: Option[String] = None,
    val creationAttempts: Integer,
    val creationDelay: FiniteDuration,
    val connectionAttempts: Option[Int] = None,
    val callCredentials: Option[CallCredentials] = None,
    val overrideAuthority: Option[String] = None,
    val sslContext: Option[SSLContext] = None,
    val deadline: Duration = Duration.Undefined,
    val userAgent: Option[String] = None,
    val useTls: Boolean = true,
    val grpcLoadBalancingType: Option[String] = None,
    val channelBuilderOverrides: NettyChannelBuilder => NettyChannelBuilder = identity) {

  /**
   * If using ServiceDiscovery and no port is returned use this one.
   */
  def withDefaultPort(value: Int): GrpcClientSettings = copy(defaultPort = value)
  def withCallCredentials(value: CallCredentials): GrpcClientSettings = copy(callCredentials = Option(value))
  def withOverrideAuthority(value: String): GrpcClientSettings = copy(overrideAuthority = Option(value))
  def withSSLContext(context: SSLContext) = copy(sslContext = Option(context))
  def withResolveTimeout(value: FiniteDuration): GrpcClientSettings = copy(resolveTimeout = value)

  /**
   * When using service discovery, port name is the optional parameter to filter the requests. Looking up a service
   * may return multiple ports (http/https/...) if the remote process only serves the grpc service on a specific port
   * you must use this setting.
   */
  def withServicePortName(servicePortName: String): GrpcClientSettings = copy(servicePortName = Some(servicePortName))
  def withServiceProtocol(serviceProtocol: String): GrpcClientSettings = copy(serviceProtocol = Some(serviceProtocol))

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

  def withCreationAttempts(value: Int): GrpcClientSettings =
    copy(creationAttempts = value)
  def withCreationDelay(delay: FiniteDuration): GrpcClientSettings =
    copy(creationDelay = delay)

  /**
   * How many times to retry establishing a connection before failing the client
   * Failure can be monitored using client.stopped and monitoring the Future/CompletionStage.
   * An exponentially increasing backoff is used between attempts.
   */
  def withConnectionAttempts(value: Int): GrpcClientSettings =
    copy(connectionAttempts = Some(value))

  /**
   * To override any default channel configurations used by netty. Only for power users.
   * API may change when io.grpc:grpc-netty-shaded is replaced by io.grpc:grpc-core and Akka HTTP.
   */
  @ApiMayChange
  def withChannelBuilderOverrides(builderOverrides: NettyChannelBuilder => NettyChannelBuilder): GrpcClientSettings =
    copy(channelBuilderOverrides = builderOverrides)

  private def copy(
      serviceName: String = serviceName,
      servicePortName: Option[String] = servicePortName,
      serviceProtocol: Option[String] = serviceProtocol,
      creationAttempts: Int = creationAttempts,
      creationDelay: FiniteDuration = creationDelay,
      defaultPort: Int = defaultPort,
      callCredentials: Option[CallCredentials] = callCredentials,
      overrideAuthority: Option[String] = overrideAuthority,
      sslContext: Option[SSLContext] = sslContext,
      deadline: Duration = deadline,
      userAgent: Option[String] = userAgent,
      useTls: Boolean = useTls,
      resolveTimeout: FiniteDuration = resolveTimeout,
      connectionAttempts: Option[Int] = connectionAttempts,
      grpcLoadBalancingType: Option[String] = grpcLoadBalancingType,
      channelBuilderOverrides: NettyChannelBuilder => NettyChannelBuilder = channelBuilderOverrides)
      : GrpcClientSettings =
    new GrpcClientSettings(
      callCredentials = callCredentials,
      serviceDiscovery = serviceDiscovery,
      servicePortName = servicePortName,
      serviceProtocol = serviceProtocol,
      creationAttempts = creationAttempts,
      creationDelay = creationDelay,
      deadline = deadline,
      serviceName = serviceName,
      overrideAuthority = overrideAuthority,
      defaultPort = defaultPort,
      sslContext = sslContext,
      userAgent = userAgent,
      useTls = useTls,
      resolveTimeout = resolveTimeout,
      connectionAttempts = connectionAttempts,
      grpcLoadBalancingType = grpcLoadBalancingType,
      channelBuilderOverrides = channelBuilderOverrides)
}
