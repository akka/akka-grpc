package example.myapp

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.{Http2, HttpsConnectionContext}

import example.myapp.echo.EchoServiceImpl
import example.myapp.echo.grpc.EchoServiceHandler

import example.myapp.helloworld.GreeterServiceImpl
import example.myapp.helloworld.grpc.GreeterServiceHandler

object Main extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val echoHandler = EchoServiceHandler(new EchoServiceImpl)
  val greeterHandler = GreeterServiceHandler(new GreeterServiceImpl)

  Http2().bindAndHandleAsync(
    echoHandler
      .orElse(greeterHandler)
    ,
    interface = "localhost",
    port = 8443,
    connectionContext = serverHttpContext())

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
