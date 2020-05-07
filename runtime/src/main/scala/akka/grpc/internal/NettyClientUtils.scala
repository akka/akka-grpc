/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.lang.reflect.Field
import java.util.concurrent.TimeUnit

import akka.Done
import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import io.grpc.CallOptions
import io.grpc.netty.shaded.io.grpc.netty.{ GrpcSslContexts, NegotiationType, NettyChannelBuilder }
import io.grpc.netty.shaded.io.netty.handler.ssl._
import javax.net.ssl.SSLContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Promise }

/**
 * INTERNAL API
 */
@InternalApi
object NettyClientUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  def createChannel(settings: GrpcClientSettings, log: LoggingAdapter)(
      implicit ec: ExecutionContext): InternalChannel = {
    var builder =
      NettyChannelBuilder
      // Not sure why netty wants to be able to shoe-horn the target into a URI... but ok,
      // we follow their lead and encode the service name as the 'authority' of the URI.
        .forTarget("//" + settings.serviceName)
        .flowControlWindow(NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW)
        .nameResolverFactory(
          new AkkaDiscoveryNameResolverProvider(
            settings.serviceDiscovery,
            settings.defaultPort,
            settings.servicePortName,
            settings.serviceProtocol,
            settings.resolveTimeout))

    if (!settings.useTls)
      builder = builder.usePlaintext()
    else {
      builder = settings.sslContext
        .map(javaCtx => builder.negotiationType(NegotiationType.TLS).sslContext(nettyHttp2SslContext(javaCtx)))
        .getOrElse(builder.negotiationType(NegotiationType.PLAINTEXT))
    }

    builder = settings.grpcLoadBalancingType.map(builder.defaultLoadBalancingPolicy(_)).getOrElse(builder)
    builder = settings.overrideAuthority.map(builder.overrideAuthority(_)).getOrElse(builder)
    builder = settings.userAgent.map(builder.userAgent(_)).getOrElse(builder)
    builder = settings.channelBuilderOverrides(builder)

    val channel = builder.build()

    val promise = Promise[Done]()
    ChannelUtils.monitorChannel(promise, channel, settings.connectionAttempts, log)

    InternalChannel(channel, promise.future)
  }

  /**
   * INTERNAL API
   *
   * Given a Java [[SSLContext]], create a Netty [[SslContext]] that can be used to build
   * a Netty HTTP/2 channel.
   */
  @InternalApi
  private def nettyHttp2SslContext(javaSslContext: SSLContext): SslContext = {
    // FIXME: Create a JdkSslContext using a normal constructor. Need to work out sensible values for all args first.
    // In the meantime, use a Netty SslContextBuild to create a JdkSslContext, then use reflection to patch the
    // object's internal SSLContext. It's not pretty, but it gets something working for now.

    // Create a Netty JdkSslContext object with all the correct ciphers, protocol settings, etc initialized.
    val nettySslContext: JdkSslContext =
      GrpcSslContexts.configure(GrpcSslContexts.forClient, SslProvider.JDK).build.asInstanceOf[JdkSslContext]

    // Patch the SSLContext value inside the JdkSslContext object
    val nettySslContextField: Field = classOf[JdkSslContext].getDeclaredField("sslContext")
    nettySslContextField.setAccessible(true)
    nettySslContextField.set(nettySslContext, javaSslContext)

    nettySslContext
  }

  /**
   * INTERNAL API
   */
  @InternalApi def callOptions(settings: GrpcClientSettings): CallOptions =
    settings.callCredentials.map(CallOptions.DEFAULT.withCallCredentials).getOrElse(CallOptions.DEFAULT)

  /**
   * INTERNAL API
   */
  @InternalApi private[akka] def callOptionsWithDeadline(
      defaultOptions: CallOptions,
      settings: GrpcClientSettings): CallOptions =
    settings.deadline match {
      case d: FiniteDuration => defaultOptions.withDeadlineAfter(d.toMillis, TimeUnit.MILLISECONDS)
      case _                 => defaultOptions
    }
}
