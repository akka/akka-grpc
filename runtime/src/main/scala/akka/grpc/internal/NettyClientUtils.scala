/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.internal

import java.io.File

import akka.annotation.InternalApi
import akka.grpc.GrpcClientSettings
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.{ GrpcSslContexts, NegotiationType, NettyChannelBuilder }
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext

/**
 * INTERNAL API
 */
@InternalApi
object NettyClientUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  def createChannel(settings: GrpcClientSettings): ManagedChannel = {
    var builder =
      NettyChannelBuilder
        .forAddress(settings.host, settings.port)
        .flowControlWindow(65 * 1024)

    builder = settings.certificate.map(c => GrpcSslContexts.forClient.trustManager(loadCert(c)).build)
      .map(ctx => builder.negotiationType(NegotiationType.TLS).sslContext(ctx))
      .getOrElse(builder.negotiationType(NegotiationType.PLAINTEXT))
    builder = settings.overrideAuthority
      .map(builder.overrideAuthority(_)).getOrElse(builder)

    builder.build
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

}
