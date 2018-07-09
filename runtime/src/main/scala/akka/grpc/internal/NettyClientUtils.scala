/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.File
import java.util.concurrent.{ ThreadLocalRandom, TimeUnit }

import scala.concurrent.duration.FiniteDuration
import akka.annotation.InternalApi
import akka.discovery.SimpleServiceDiscovery
import akka.grpc.GrpcClientSettings
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.{ GrpcSslContexts, NegotiationType, NettyChannelBuilder }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * INTERNAL API
 */
@InternalApi
object NettyClientUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  def createChannel(settings: GrpcClientSettings)(implicit ec: ExecutionContext): Future[ManagedChannel] = {
    settings.serviceDiscovery.lookup(settings.name, settings.resolveTimeout).flatMap { targets: SimpleServiceDiscovery.Resolved =>
      if (targets.addresses.nonEmpty) {
        val target = targets.addresses(ThreadLocalRandom.current().nextInt(targets.addresses.size))
        var builder =
          NettyChannelBuilder
            .forAddress(target.host, target.port.getOrElse(settings.defaultPort))
            .flowControlWindow(NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW)

        if (!settings.useTls)
          builder = builder.usePlaintext()
        else {
          builder = settings.trustedCaCertificate.map(c => GrpcSslContexts.forClient.trustManager(loadCert(c)).build)
            .map(ctx => builder.negotiationType(NegotiationType.TLS).sslContext(ctx))
            .getOrElse(builder.negotiationType(NegotiationType.PLAINTEXT))
          builder = settings.overrideAuthority.map(builder.overrideAuthority(_)).getOrElse(builder)
        }

        builder = settings.userAgent.map(builder.userAgent(_)).getOrElse(builder)

        Future.successful(builder.build)
      } else {
        Future.failed(new IllegalStateException("No targets returned for name: " + settings.name))
      }
    }
  }

  // FIXME couldn't we use the inputstream based trustManager() method instead of going via filesystem?
  private def loadCert(name: String): File = {
    import java.io._

    val in = new BufferedInputStream(this.getClass.getResourceAsStream("/certs/" + name))
    val inBytes: Array[Byte] = {
      val baos = new ByteArrayOutputStream(math.max(64, in.available()))
      val buffer = Array.ofDim[Byte](32 * 1024)

      var bytesRead = in.read(buffer)
      while (bytesRead >= 0) {
        baos.write(buffer, 0, bytesRead)
        bytesRead = in.read(buffer)
      }
      baos.toByteArray
    }

    val tmpFile = File.createTempFile(name, "")
    tmpFile.deleteOnExit()

    val out = new BufferedOutputStream(new FileOutputStream(tmpFile))
    out.write(inBytes)
    out.close()

    tmpFile
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
