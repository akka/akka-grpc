/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.io.{ BufferedInputStream, IOException, InputStream }
import java.security.KeyStore
import java.security.cert.{ CertificateFactory, X509Certificate }

import javax.net.ssl.{ TrustManager, TrustManagerFactory }

object SSLContextUtils {
  def trustManagerFromStream(certStream: InputStream): TrustManager = {
    try {
      import scala.collection.JavaConverters._
      val cf = CertificateFactory.getInstance("X.509")
      val bis = new BufferedInputStream(certStream)

      val keystore = KeyStore.getInstance(KeyStore.getDefaultType)
      keystore.load(null)
      cf.generateCertificates(bis).asScala.foreach { cert =>
        val alias = cert.asInstanceOf[X509Certificate].getSubjectX500Principal.getName
        keystore.setCertificateEntry(alias, cert)
      }

      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      tmf.init(keystore)
      tmf.getTrustManagers()(0)
    } finally certStream.close()
  }

  def trustManagerFromResource(certificateResourcePath: String): TrustManager = {
    val certStream: InputStream = getClass.getResourceAsStream(certificateResourcePath)
    if (certStream == null) throw new IOException(s"Couldn't find '$certificateResourcePath' on the classpath")
    trustManagerFromStream(certStream)
  }
}
