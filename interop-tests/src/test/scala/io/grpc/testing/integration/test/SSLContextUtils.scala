package io.grpc.testing.integration.test

import java.io.{ IOException, InputStream }

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.netty.handler.ssl.{ JdkSslContext, SslProvider }
import javax.net.ssl.SSLContext

object SSLContextUtils {

  def sslContextForCert(certPath: String): SSLContext = {
    // Use Netty's SslContextBuilder internally to help us construct a SSLContext
    val fullCertPath = "/certs/" + certPath
    val certStream: InputStream = getClass.getResourceAsStream(fullCertPath)
    if (certStream == null) throw new IOException(s"Couldn't find '$fullCertPath' on the classpath")
    val sslBuilder = try { GrpcSslContexts.forClient.trustManager(certStream) } finally certStream.close()
    GrpcSslContexts.configure(sslBuilder, SslProvider.JDK).build.asInstanceOf[JdkSslContext].context
  }

}
