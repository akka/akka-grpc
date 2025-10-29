/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server.{ Directive0, Route }
import akka.http.scaladsl.server.Directives._
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc._

import scala.concurrent.{ ExecutionContext, Future }

object AuthenticatedGreeterServer {
  def main(args: Array[String]): Unit = {
    // Important: enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val conf =
      ConfigFactory.parseString("akka.http.server.enable-http2 = on").withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem("HelloWorld", conf)
    new AuthenticatedGreeterServer(system).run()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}

class AuthenticatedGreeterServer(system: ActorSystem) {
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    //#http-route
    // A Route to authenticate with
    val authenticationRoute: Route = path("login") {
      get {
        complete("Psst, please use token XYZ!")
      }
    }
    //#http-route

    //#grpc-route
    // Create service handlers
    val handler: HttpRequest => Future[HttpResponse] =
      GreeterServiceHandler(new GreeterServiceImpl())

    // As a Route
    val handlerRoute: Route = handle(handler)
    //#grpc-route

    //#grpc-protected
    // A directive to authorize calls
    val authorizationDirective: Directive0 =
      headerValueByName("token").flatMap { token =>
        if (token == "XYZ") pass
        else reject
      }
    //#grpc-protected

    //#combined
    val route = concat(
      authenticationRoute,
      authorizationDirective {
        handlerRoute
      })

    // Bind service handler servers to localhost:8082
    val binding = Http().newServerAt("127.0.0.1", 8082).bind(route)
    //#combined

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}
