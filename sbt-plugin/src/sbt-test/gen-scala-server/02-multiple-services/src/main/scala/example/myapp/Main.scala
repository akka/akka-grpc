package example.myapp

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.{Http, HttpsConnectionContext}

import example.myapp.echo.EchoServiceImpl
import example.myapp.echo.grpc.EchoServiceHandler

import example.myapp.helloworld.GreeterServiceImpl
import example.myapp.helloworld.grpc.GreeterServiceHandler

object Main extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val echoHandler = EchoServiceHandler.partial(new EchoServiceImpl)
  val greeterHandler = GreeterServiceHandler.partial(new GreeterServiceImpl)
  val serviceHandler = ServiceHandler.concatOrNotFound(echoHandler, greeterHandler)

  Http()
    .newServerAt("localhost", 8443)
    .enableHttps(serverHttpContext())
    .bind(serviceHandler);

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
