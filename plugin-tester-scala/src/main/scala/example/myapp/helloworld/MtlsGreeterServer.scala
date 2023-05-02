/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-server
package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.AttributeKeys
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.pki.pem.DERPrivateKeyLoader
import akka.pki.pem.PEMDecoder
import com.typesafe.config.ConfigFactory
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
    // Important: enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem("HelloWorld", conf)
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

    val serviceWithExtraCertInspection: HttpRequest => Future[HttpResponse] = { request =>
      log.info("Client request for: {}", request.uri.path)
      // we know it's present because requiring client auth in HTTPS setup
      request.attribute(AttributeKeys.sslSession).foreach { sslSessionInfo =>
        log.info("Client cert: {}", sslSessionInfo.session.getPeerCertificates.toList)
      }
      // we could deny the request here based on some property of the session/cert
      service(request)
    }

    // Bind service handler servers to localhost:8443
    val binding =
      Http().newServerAt("127.0.0.1", 8443).enableHttps(serverHttpContext).bind(serviceWithExtraCertInspection)

    // report successful binding
    binding.foreach { binding => log.info(s"gRPC server bound to: {}", binding.localAddress) }

    binding
  }

  private def serverHttpContext: HttpsConnectionContext = {
    val privateKey =
      DERPrivateKeyLoader.load(PEMDecoder.decode(readPrivateKeyPem()))
    val certFactory = CertificateFactory.getInstance("X.509")

    // keyStore is for the server cert and private key
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null)
    keyStore.setKeyEntry(
      "private",
      privateKey,
      // No password for our private key
      new Array[Char](0),
      Array[Certificate](certFactory.generateCertificate(getClass.getResourceAsStream("/certs/localhost-server.pem"))))
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, null)
    val keyManagers = keyManagerFactory.getKeyManagers

    // trustStore is for what client certs the server trust
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
        certFactory.generateCertificate(getClass.getResourceAsStream("/certs/localhost-client.crt"))),
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

  private def readPrivateKeyPem(): String =
    Source.fromResource("certs/localhost-server.key").mkString
}
