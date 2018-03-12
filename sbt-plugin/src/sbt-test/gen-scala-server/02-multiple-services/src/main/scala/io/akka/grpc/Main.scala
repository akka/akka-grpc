package io.akka.grpc

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.{Http2, HttpsConnectionContext}
import io.akka.grpc.echo.{EchoImpl, EchoHandler}
import io.akka.grpc.helloworld.{GreeterImpl, GreeterHandler}

object Main extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val echoHandler = EchoHandler(new EchoImpl)
  val greeterHandler = GreeterHandler(new GreeterImpl)

  Http2().bindAndHandleAsync(
    echoHandler.orElse(greeterHandler),
    interface = "localhost",
    port = 8443,
    httpsContext = serverHttpContext())

  private def serverHttpContext() = {
    // never put passwords into code!
    val password = "abcdef".toCharArray

    val ks = KeyStore.getInstance("PKCS12")
    ks.load(Option(getClass.getClassLoader.getResourceAsStream("server.p12")).get, password)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, null, new SecureRandom)

    new HttpsConnectionContext(context)
  }
}