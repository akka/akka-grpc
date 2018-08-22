/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp

import java.io.{ ByteArrayOutputStream, FileInputStream, InputStream }
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.{ Http, HttpConnectionContext, HttpsConnectionContext }
import akka.http.scaladsl.UseHttp2.Always
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld._
import example.myapp.helloworld.grpc._
import example.myapp.echo._
import example.myapp.echo.grpc._

//#concatOrNotFound
import akka.grpc.scaladsl.ServiceHandler

//#concatOrNotFound

object CombinedServer {

  def main(args: Array[String]): Unit = {
    // important to enable HTTP/2 in ActorSystem's config
    val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    implicit val sys: ActorSystem = ActorSystem("HelloWorld", conf)
    implicit val mat: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext = sys.dispatcher

    //#concatOrNotFound
    // explicit types not needed but included in example for clarity
    val greeterService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      example.myapp.helloworld.grpc.GreeterServiceHandler.partial(new GreeterServiceImpl(mat))
    val echoService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      EchoServiceHandler.partial(new EchoServiceImpl)
    val serviceHandlers: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(greeterService, echoService)

    Http().bindAndHandleAsync(
      serviceHandlers,
      interface = "127.0.0.1",
      port = 8080,
      // Needed to allow running multiple requests concurrently, see https://github.com/akka/akka-http/issues/2145
      parallelism = 256,
      connectionContext = HttpConnectionContext(http2 = Always))
      //#concatOrNotFound
      .foreach { binding =>
        println(s"gRPC server bound to: ${binding.localAddress}")
      }
  }
}

