package example.myapp

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.{Http2, HttpsConnectionContext}

import example.myapp.echo.EchoServiceImpl
import example.myapp.echo.grpc.EchoServiceHandler

import example.myapp.helloworld.GreeterServiceImpl
import example.myapp.helloworld.grpc.GreeterServiceHandler

object Main extends App {
  implicit val system = ActorSystem()
  implicit val mat = Materializer.matFromSystem(sys)

  val echoHandler = EchoServiceHandler.partial(new EchoServiceImpl)
  val greeterHandler = GreeterServiceHandler.partial(new GreeterServiceImpl)
  val serviceHandler = ServiceHandler.concatOrNotFound(echoHandler, greeterHandler)

  Http2().bindAndHandleAsync(
    serviceHandler,
    interface = "localhost",
    port = 8443,
    parallelism = 256, // Needed to allow running multiple requests concurrently, see https://github.com/akka/akka-http/issues/2145
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
