/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-server
package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.pki.pem.DERPrivateKeyLoader
import akka.pki.pem.PEMDecoder
import example.myapp.helloworld.grpc._
import org.slf4j.LoggerFactory

import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.Source

object MtlsGreeterServer {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("MtlsHelloWorldServer")
    new MtlsGreeterServer(system).run()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }

}

class MtlsGreeterServer(system: ActorSystem) {

  private val log = LoggerFactory.getLogger(classOf[MtlsGreeterServer])
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      GreeterServiceHandler(new GreeterServiceImpl())

    // Bind service handler servers to localhost:8443
    val binding =
      Http().newServerAt("127.0.0.1", 8443).enableHttps(serverHttpContext).bind(service)

    // report successful binding
    binding.foreach { binding => log.info(s"gRPC server bound to: {}", binding.localAddress) }

    binding
  }

  private def serverHttpContext: HttpsConnectionContext = {
    val certFactory = CertificateFactory.getInstance("X.509")

    // keyStore/keymanagers are for the server cert and private key
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null)
    val serverCert = certFactory.generateCertificate(getClass.getResourceAsStream("/certs/localhost-server.crt"))
    val serverPrivateKey =
      DERPrivateKeyLoader.load(PEMDecoder.decode(classPathFileAsString("certs/localhost-server.key")))
    keyStore.setKeyEntry(
      "private",
      serverPrivateKey,
      // No password for our private key
      new Array[Char](0),
      Array[Certificate](serverCert))
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, null)
    val keyManagers = keyManagerFactory.getKeyManagers

    // trustStore/trustManagers are for what client certs the server trust
    val trustStore = KeyStore.getInstance("PKCS12")
    trustStore.load(null)
    // any client cert signed by this CA is allowed to connect
    trustStore.setEntry(
      "rootCA",
      new KeyStore.TrustedCertificateEntry(
        certFactory.generateCertificate(getClass.getResourceAsStream("/certs/rootCA.crt"))),
      null)
    /*
    // or specific client cert (probably less useful)
    trustStore.setEntry(
      "client",
      new KeyStore.TrustedCertificateEntry(
        certFactory.generateCertificate(getClass.getResourceAsStream("/certs/client1.crt"))),
      null)
     */
    val tmf = TrustManagerFactory.getInstance("SunX509")
    tmf.init(trustStore)
    val trustManagers = tmf.getTrustManagers

    ConnectionContext.httpsServer { () =>
      val context = SSLContext.getInstance("TLS")
      context.init(keyManagers, trustManagers, new SecureRandom)

      val engine = context.createSSLEngine()
      engine.setUseClientMode(false)

      // require client certs
      engine.setNeedClientAuth(true)

      engine
    }
  }

  private def classPathFileAsString(path: String): String =
    Source.fromResource(path).mkString
}
//#full-server
