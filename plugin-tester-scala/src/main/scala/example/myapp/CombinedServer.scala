/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.http.scaladsl.{ Http, HttpConnectionContext }
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld._
import example.myapp.echo._
import example.myapp.echo.grpc._

//#concatOrNotFound
import akka.grpc.scaladsl.ServiceHandler

//#concatOrNotFound

object CombinedServer {
  def main(args: Array[String]): Unit = {
    // important to enable HTTP/2 in ActorSystem's config
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    implicit val sys: ActorSystem = ActorSystem("HelloWorld", conf)
    implicit val mat: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext = sys.dispatcher

    //#concatOrNotFound
    // explicit types not needed but included in example for clarity
    val greeterService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      example.myapp.helloworld.grpc.GreeterServiceHandler.partial(new GreeterServiceImpl())
    val echoService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      EchoServiceHandler.partial(new EchoServiceImpl)
    val serviceHandlers: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(greeterService, echoService)

    Http()
      .bindAndHandleAsync(
        serviceHandlers,
        interface = "127.0.0.1",
        port = 8080,
        connectionContext = HttpConnectionContext())
      //#concatOrNotFound
      .foreach { binding =>
        println(s"gRPC server bound to: ${binding.localAddress}")
      }
  }
}
