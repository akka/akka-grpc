/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-server
package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.Http
import example.myapp.helloworld.grpc._

import scala.concurrent.{ ExecutionContext, Future }

class PowerGreeterServer(system: ActorSystem) {
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      GreeterServicePowerApiHandler(new PowerGreeterServiceImpl())

    // Bind service handler servers to localhost:8080/8081
    val binding = Http().newServerAt("127.0.0.1", 8081).bind(service)

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}

//#full-server
