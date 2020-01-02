/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.io.{ File, FileInputStream, IOException, InputStream }

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.netty.handler.ssl.{ JdkSslContext, SslProvider }
import javax.net.ssl.SSLContext

object SSLContextUtils {
  def fromStream(certStream: InputStream): SSLContext = {
    val sslBuilder =
      try {
        GrpcSslContexts.forClient.trustManager(certStream)
      } finally certStream.close()
    GrpcSslContexts.configure(sslBuilder, SslProvider.JDK).build.asInstanceOf[JdkSslContext].context
  }

  def sslContextFromResource(certificateResourcePath: String): SSLContext = {
    // Use Netty's SslContextBuilder internally to help us construct a SSLContext
    val certStream: InputStream = getClass.getResourceAsStream(certificateResourcePath)
    if (certStream == null) throw new IOException(s"Couldn't find '$certificateResourcePath' on the classpath")
    fromStream(certStream)
  }

  def sslContextFromFile(certPath: File): SSLContext =
    fromStream(new FileInputStream(certPath))
}
