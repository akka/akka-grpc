/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.grpc.scaladsl.ServerReflection
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld._
import example.myapp.echo._
import example.myapp.echo.grpc._
import example.myapp.helloworld.grpc.GreeterService

//#concatOrNotFound
import akka.grpc.scaladsl.ServiceHandler

//#concatOrNotFound

//#grpc-web
import akka.grpc.scaladsl.WebHandler

//#grpc-web

object CombinedServer {
  def main(args: Array[String]): Unit = {
    // important to enable HTTP/2 in ActorSystem's config
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    implicit val sys: ActorSystem = ActorSystem("HelloWorld", conf)
    implicit val ec: ExecutionContext = sys.dispatcher

    //#concatOrNotFound
    // explicit types not needed but included in example for clarity
    val greeterService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      example.myapp.helloworld.grpc.GreeterServiceHandler.partial(new GreeterServiceImpl())
    val echoService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      EchoServiceHandler.partial(new EchoServiceImpl)
    val reflectionService = ServerReflection.partial(List(GreeterService, EchoService))
    val serviceHandlers: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(greeterService, echoService, reflectionService)

    Http()
      .newServerAt("127.0.0.1", 8080)
      .bind(serviceHandlers)
      //#concatOrNotFound
      .foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    //#grpc-web
    val grpcWebServiceHandlers = WebHandler.grpcWebHandler(greeterService, echoService)

    Http()
      .newServerAt("127.0.0.1", 8081)
      .bind(grpcWebServiceHandlers)
      //#grpc-web
      .foreach { binding => println(s"gRPC-Web server bound to: ${binding.localAddress}") }
  }
}
