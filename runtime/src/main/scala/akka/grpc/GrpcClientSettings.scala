/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.actor.ClassicActorSystemProvider
import akka.annotation.{ ApiMayChange, InternalApi }
import akka.discovery.{ Discovery, ServiceDiscovery }
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.grpc.internal.HardcodedServiceDiscovery
import akka.util.Helpers
import akka.util.JavaDurationConverters._
import com.typesafe.config.{ Config, ConfigValueFactory }
import io.grpc.CallCredentials
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider
import javax.net.ssl.{ SSLContext, TrustManager }

import scala.collection.immutable
import scala.concurrent.duration.{ Duration, _ }

object GrpcClientSettings {

  /**
   * Create a client that uses a static host and port. Default configuration
   * is loaded from reference.conf
   */
  def connectToServiceAt(host: String, port: Int)(
      implicit actorSystem: ClassicActorSystemProvider): GrpcClientSettings = {
    val system = actorSystem.classicSystem
    // default is static
    val defaultServiceConfig = system.settings.config
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
  def fromConfig(clientName: String)(implicit actorSystem: ClassicActorSystemProvider): GrpcClientSettings = {
    val system = actorSystem.classicSystem
    val akkaGrpcClientConfig = system.settings.config.getConfig("akka.grpc.client")
    val clientConfig = {
      // Use config named "*" by default
      val defaultServiceConfig = akkaGrpcClientConfig.getConfig("\"*\"")
      require(
        akkaGrpcClientConfig.hasPath(s""""$clientName""""),
        s"Config path `akka.grpc.client.$clientName` does not exist")
      akkaGrpcClientConfig.getConfig(s""""$clientName"""").withFallback(defaultServiceConfig)
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
  def usingServiceDiscovery(serviceName: String)(
      implicit actorSystem: ClassicActorSystemProvider): GrpcClientSettings = {
    val clientConfiguration: Config =
      actorSystem.classicSystem.settings.config.getConfig("akka.grpc.client").getConfig("\"*\"")
    val resolveTimeout = clientConfiguration.getDuration("service-discovery.resolve-timeout").asScala
    val discovery = Discovery.get(actorSystem).discovery
    withConfigDefaults(serviceName, discovery, -1, resolveTimeout, clientConfiguration)
  }

  /**
   * Configure the client to lookup a valid hostname:port from a service registry accessed via the provided `ServiceDiscovery`.
   * When invoking a lookup operation on the service registry, a
   * name is required and optionally a port name and a protocol. This factory method only requires a `serviceName`.
   * Use `withServicePortName` and `withServiceProtocol` to refine the lookup on the service registry.
   *
   * @param serviceName name of the remote service to lookup.
   */
  def usingServiceDiscovery(serviceName: String, discovery: ServiceDiscovery)(
      implicit actorSystem: ClassicActorSystemProvider): GrpcClientSettings = {
    val clientConfiguration: Config =
      actorSystem.classicSystem.settings.config.getConfig("akka.grpc.client").getConfig("\"*\"")
    val resolveTimeout = clientConfiguration.getDuration("service-discovery.resolve-timeout").asScala
    withConfigDefaults(serviceName, discovery, -1, resolveTimeout, clientConfiguration)
  }

  /**
   * Configure client via the provided Config. See reference.conf for configuration properties.
   */
  def fromConfig(clientConfiguration: Config)(implicit sys: ClassicActorSystemProvider): GrpcClientSettings = {
    val serviceDiscoveryMechanism = clientConfiguration.getString("service-discovery.mechanism")
    var serviceName = clientConfiguration.getString("service-discovery.service-name")
    val port = clientConfiguration.getInt("port")
    val resolveTimeout = clientConfiguration.getDuration("service-discovery.resolve-timeout").asScala
    val sd = serviceDiscoveryMechanism match {
      case "static" | "grpc-dns" =>
        val host = clientConfiguration.getString("host")
        require(host.nonEmpty, "host can't be empty when service-discovery-mechanism is set to static or grpc-dns")
        // Required by the Discovery infrastructure, set to host as we use static or grpc-dns discovery.
        serviceName = host
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
      clientConfiguration: Config): GrpcClientSettings =
    new GrpcClientSettings(
      serviceName,
      serviceDiscovery,
      defaultPort,
      resolveTimeout,
      getOptionalString(clientConfiguration, "service-discovery.port-name"),
      getOptionalString(clientConfiguration, "service-discovery.protocol"),
      getOptionalInt(clientConfiguration, "connection-attempts"),
      None,
      getOptionalString(clientConfiguration, "override-authority"),
      getOptionalString(clientConfiguration, "ssl-provider").map({
        case "jdk"            => SslProvider.JDK
        case "openssl"        => SslProvider.OPENSSL
        case "openssl_refcnt" => SslProvider.OPENSSL_REFCNT
        case other =>
          throw new IllegalArgumentException(
            s"ssl-provider: expected empty, 'jdk', 'openssl' or 'openssl_refcnt', but got [$other]")
      }),
      None,
      getOptionalString(clientConfiguration, "trusted").map(SSLContextUtils.trustManagerFromResource),
      getPotentiallyInfiniteDuration(clientConfiguration, "deadline"),
      getOptionalString(clientConfiguration, "user-agent"),
      clientConfiguration.getBoolean("use-tls"),
      getOptionalString(clientConfiguration, "load-balancing-policy"))

  private def getOptionalString(config: Config, path: String): Option[String] =
    config.getString(path) match {
      case ""    => None
      case other => Some(other)
    }

  private def getOptionalInt(config: Config, path: String): Option[Int] =
    config.getInt(path) match {
      case -1    => None // retry forever
      case other => Some(other)
    }

  private def getPotentiallyInfiniteDuration(underlying: Config, path: String): Duration =
    Helpers.toRootLowerCase(underlying.getString(path)) match {
      case "infinite" => Duration.Inf
      case _          => Duration.fromNanos(underlying.getDuration(path).toNanos)
    }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[grpc] def staticServiceDiscovery(host: String, port: Int) =
    new HardcodedServiceDiscovery(Resolved(host, immutable.Seq(ResolvedTarget(host, Some(port), None))))

}

@ApiMayChange
final class GrpcClientSettings private (
    val serviceName: String,
    val serviceDiscovery: ServiceDiscovery,
    val defaultPort: Int,
    val resolveTimeout: FiniteDuration,
    val servicePortName: Option[String],
    val serviceProtocol: Option[String],
    val connectionAttempts: Option[Int],
    val callCredentials: Option[CallCredentials],
    val overrideAuthority: Option[String],
    val sslProvider: Option[SslProvider],
    val sslContext: Option[SSLContext],
    val trustManager: Option[TrustManager],
    val deadline: Duration,
    val userAgent: Option[String],
    val useTls: Boolean,
    val loadBalancingPolicy: Option[String],
    val channelBuilderOverrides: NettyChannelBuilder => NettyChannelBuilder = identity) {
  require(
    sslContext.isEmpty || trustManager.isEmpty,
    "Configuring the sslContext or the trustManager is mutually exclusive")
  require(
    if (sslContext.isDefined) sslProvider.forall(_ == SslProvider.JDK) else true,
    "When sslContext is configured, sslProvider must not set to something different than JDK")

  /**
   * If using ServiceDiscovery and no port is returned use this one.
   */
  def withDefaultPort(value: Int): GrpcClientSettings = copy(defaultPort = value)
  def withCallCredentials(value: CallCredentials): GrpcClientSettings = copy(callCredentials = Option(value))
  def withOverrideAuthority(value: String): GrpcClientSettings = copy(overrideAuthority = Option(value))
  def withSslProvider(sslProvider: SslProvider): GrpcClientSettings = copy(sslProvider = Option(sslProvider))

  /**
   * Prefer using `withContextManager`: withSslContext forces the ssl-provider 'jdk', which is known
   * not to work on JDK 1.8.0_252.
   */
  def withSslContext(sslContext: SSLContext): GrpcClientSettings = copy(sslContext = Option(sslContext))
  def withTrustManager(trustManager: TrustManager): GrpcClientSettings = copy(trustManager = Option(trustManager))
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

  def withLoadBalancingPolicy(loadBalancingPolicy: String): GrpcClientSettings =
    copy(loadBalancingPolicy = Some(loadBalancingPolicy))

  @deprecated("use withLoadBalancingPolicy", since = "1.0.0")
  def withGrpcLoadBalancingType(loadBalancingType: String): GrpcClientSettings =
    withLoadBalancingPolicy(loadBalancingType)

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
      defaultPort: Int = defaultPort,
      callCredentials: Option[CallCredentials] = callCredentials,
      overrideAuthority: Option[String] = overrideAuthority,
      sslProvider: Option[SslProvider] = sslProvider,
      sslContext: Option[SSLContext] = sslContext,
      trustManager: Option[TrustManager] = trustManager,
      deadline: Duration = deadline,
      userAgent: Option[String] = userAgent,
      useTls: Boolean = useTls,
      resolveTimeout: FiniteDuration = resolveTimeout,
      connectionAttempts: Option[Int] = connectionAttempts,
      loadBalancingPolicy: Option[String] = loadBalancingPolicy,
      channelBuilderOverrides: NettyChannelBuilder => NettyChannelBuilder = channelBuilderOverrides)
      : GrpcClientSettings =
    new GrpcClientSettings(
      callCredentials = callCredentials,
      serviceDiscovery = serviceDiscovery,
      servicePortName = servicePortName,
      serviceProtocol = serviceProtocol,
      deadline = deadline,
      serviceName = serviceName,
      overrideAuthority = overrideAuthority,
      defaultPort = defaultPort,
      sslProvider = sslProvider,
      sslContext = sslContext,
      trustManager = trustManager,
      userAgent = userAgent,
      useTls = useTls,
      resolveTimeout = resolveTimeout,
      connectionAttempts = connectionAttempts,
      loadBalancingPolicy = loadBalancingPolicy,
      channelBuilderOverrides = channelBuilderOverrides)
}
