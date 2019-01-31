/**
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.lang.reflect.Field
import java.util.concurrent.{ ThreadLocalRandom, TimeUnit }

import akka.Done
import akka.annotation.InternalApi
import akka.discovery.Lookup
import akka.grpc.GrpcClientSettings
import io.grpc.netty.shaded.io.grpc.netty.{ GrpcSslContexts, NegotiationType, NettyChannelBuilder }
import io.grpc.netty.shaded.io.netty.handler.ssl._
import io.grpc.{ CallOptions, ManagedChannel }
import javax.net.ssl.SSLContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }

/**
 * INTERNAL API
 */
@InternalApi
object NettyClientUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  def createChannel(settings: GrpcClientSettings)(implicit ec: ExecutionContext): InternalChannel = {
    val promise = Promise[Done]()
    val mc: Future[ManagedChannel] = settings.serviceDiscovery.lookup(
      Lookup(settings.serviceName, settings.servicePortName, settings.serviceProtocol),
      settings.resolveTimeout).flatMap { targets =>
        if (targets.addresses.nonEmpty) {
          val target = targets.addresses(ThreadLocalRandom.current().nextInt(targets.addresses.size))
          var builder =
            NettyChannelBuilder
              .forAddress(target.host, target.port.getOrElse(settings.defaultPort))
              .flowControlWindow(NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW)

          if (!settings.useTls)
            builder = builder.usePlaintext()
          else {
            builder = settings.sslContext
              .map(javaCtx => builder.negotiationType(NegotiationType.TLS).sslContext(nettyHttp2SslContext(javaCtx)))
              .getOrElse(builder.negotiationType(NegotiationType.PLAINTEXT))

            builder = settings.overrideAuthority.map(builder.overrideAuthority(_)).getOrElse(builder)
          }

          builder = settings.userAgent.map(builder.userAgent(_)).getOrElse(builder)
          builder = settings.channelBuilderOverrides(builder)

          val channel = builder.build()
          ChannelUtils.monitorChannel(promise, channel, settings.connectionAttempts)
          Future.successful(channel)
        } else {
          Future.failed(new IllegalStateException("No targets returned for name: " + settings.serviceName))
        }
      }
    new InternalChannel(mc, promise)
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
    val nettySslContext: JdkSslContext = GrpcSslContexts
      .configure(GrpcSslContexts.forClient, SslProvider.JDK)
      .build.asInstanceOf[JdkSslContext]

    // Patch the SSLContext value inside the JdkSslContext object
    val nettySslContextField: Field = classOf[JdkSslContext].getDeclaredField("sslContext")
    nettySslContextField.setAccessible(true)
    nettySslContextField.set(nettySslContext, javaSslContext)

    nettySslContext
  }
  /**
   * INTERNAL API
   */
  @InternalApi def callOptions(settings: GrpcClientSettings): CallOptions = {
    settings.callCredentials.map(CallOptions.DEFAULT.withCallCredentials).getOrElse(CallOptions.DEFAULT)
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[akka] def callOptionsWithDeadline(defaultOptions: CallOptions, settings: GrpcClientSettings): CallOptions =
    settings.deadline match {
      case d: FiniteDuration => defaultOptions.withDeadlineAfter(d.toMillis, TimeUnit.MILLISECONDS)
      case _ => defaultOptions
    }

}
