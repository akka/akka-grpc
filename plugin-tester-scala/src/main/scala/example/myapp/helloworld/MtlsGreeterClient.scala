package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.pki.pem.{ DERPrivateKeyLoader, PEMDecoder }
import example.myapp.helloworld.grpc.GreeterServiceClient
import example.myapp.helloworld.grpc.HelloRequest

import java.security.{ KeyStore, SecureRandom }
import java.security.cert.{ Certificate, CertificateFactory }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.Success
import scala.util.Failure

object MtlsGreeterClient {

  def main(args: Array[String]): Unit = {
    implicit val sys: ActorSystem = ActorSystem.create("MtlsHelloWorldClient")
    implicit val ec: ExecutionContext = sys.dispatcher

    val clientSettings = GrpcClientSettings.connectToServiceAt("localhost", 8443).withSslContext(sslContext())

    val client = GreeterServiceClient(clientSettings)

    val reply = client.sayHello(HelloRequest("Jonas"))

    reply.onComplete { tryResponse =>
      tryResponse match {
        case Success(reply) =>
          println(s"Successful reply: $reply")
        case Failure(exception) =>
          println("Request failed")
          exception.printStackTrace()
      }
      sys.terminate()
    }
  }

  private def sslContext(): SSLContext = {
    val clientPrivateKey =
      DERPrivateKeyLoader.load(PEMDecoder.decode(classPathFileAsString("certs/client1.key")))
    val certFactory = CertificateFactory.getInstance("X.509")

    // keyStore is for the client cert and private key
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null)
    keyStore.setKeyEntry(
      "private",
      clientPrivateKey,
      // No password for our private client key
      new Array[Char](0),
      Array[Certificate](certFactory.generateCertificate(getClass.getResourceAsStream("/certs/client1.crt"))))
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, null)
    val keyManagers = keyManagerFactory.getKeyManagers

    // trustStore is for what server certs the client trust
    val trustStore = KeyStore.getInstance("PKCS12")
    trustStore.load(null)
    // accept any server cert signed by this CA
    trustStore.setEntry(
      "rootCA",
      new KeyStore.TrustedCertificateEntry(
        certFactory.generateCertificate(getClass.getResourceAsStream("/certs/rootCA.crt"))),
      null)
    val tmf = TrustManagerFactory.getInstance("SunX509")
    tmf.init(trustStore)
    val trustManagers = tmf.getTrustManagers

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagers, trustManagers, new SecureRandom())
    context
  }

  private def classPathFileAsString(path: String): String =
    Source.fromResource(path).mkString

}
