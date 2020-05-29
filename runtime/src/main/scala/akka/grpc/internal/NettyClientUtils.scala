/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

import akka.Done
import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import io.grpc.CallOptions
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.{ SslContext, SslContextBuilder }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.util.{ Failure, Success }

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
      builder = builder.negotiationType(NegotiationType.TLS)
      builder = settings.sslContext match {
        case Some(sslContext) =>
          builder.sslContext(createNettySslContext(sslContext))
        case None =>
          (settings.trustManager, settings.sslProvider) match {
            case (None, None) =>
              builder
            case (tm, provider) =>
              val context = provider match {
                case None =>
                  GrpcSslContexts.configure(SslContextBuilder.forClient())
                case Some(sslProvider) =>
                  GrpcSslContexts.configure(SslContextBuilder.forClient(), sslProvider)
              }
              builder.sslContext((tm match {
                case None               => context
                case Some(trustManager) => context.trustManager(trustManager)
              }).build())
          }
      }
    }

    builder = settings.grpcLoadBalancingType.map(builder.defaultLoadBalancingPolicy(_)).getOrElse(builder)
    builder = settings.overrideAuthority.map(builder.overrideAuthority(_)).getOrElse(builder)
    builder = settings.userAgent.map(builder.userAgent(_)).getOrElse(builder)
    builder = settings.channelBuilderOverrides(builder)

    val connectionAttempts = settings.grpcLoadBalancingType match {
      case None | Some("pick_first") => settings.connectionAttempts
      case _                         =>
        // When loadbalancing we cannot count the individual attempts, so
        // the only options are '1' ('don't retry') or 'retry indefinitely'
        settings.connectionAttempts.flatMap {
          case 1 => Some(1)
          case _ => None
        }
    }

    val channel = builder.build()

    val channelReadyPromise = Promise[Unit]()
    val channelClosedPromise = Promise[Done]()

    ChannelUtils.monitorChannel(channelReadyPromise, channelClosedPromise, channel, connectionAttempts, log)

    channelReadyPromise.future.onComplete {
      case Success(()) =>
      // OK!
      case Failure(e) =>
        // shutdown is idempotent in ManagedChannelImpl
        channel.shutdown()
        channelClosedPromise.tryFailure(e)
    }

    InternalChannel(channel, channelClosedPromise.future)
  }

  /**
   * INTERNAL API
   *
   * Given a Java [[SSLContext]], create a Netty [[SslContext]] that can be used to build
   * a Netty HTTP/2 channel.
   */
  @InternalApi
  private def createNettySslContext(javaSslContext: SSLContext): SslContext = {
    import io.grpc.netty.shaded.io.netty.handler.ssl._
    import java.lang.reflect.Field

    // This is a hack for situations where the SSLContext is given.
    // This approach forces using SslProvider.JDK, which is known not to work
    // on JDK 1.8.0_252

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
