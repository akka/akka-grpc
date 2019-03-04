/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-server
package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.http.scaladsl.UseHttp2.Always
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.{ Http, HttpConnectionContext }
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc._

import scala.concurrent.{ ExecutionContext, Future }

object GreeterServer {

  def main(args: Array[String]): Unit = {
    // Important: enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem("HelloWorld", conf)
    new GreeterServer(system).run()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}

class GreeterServer(system: ActorSystem) {

  def run(): Future[Seq[Http.ServerBinding]] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val mat: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext = sys.dispatcher

    // Create service handlers
    //    val service: HttpRequest => Future[HttpResponse] =
    //      GreeterServiceHandler(new GreeterServiceImpl(mat))
    val services: Seq[HttpRequest => Future[HttpResponse]] = Seq(
      GreeterServiceHandler(new GreeterServiceImpl()),
      GreeterServicePowerApiHandler(new GreeterServicePowerApiImpl(mat)))

    // Bind service handler servers to localhost:8080/8081
    val bindings = Future.sequence {
      services
        .zip(Seq(8080, 8081))
        .map {
          case (service, port) =>
            Http().bindAndHandleAsync(
              service,
              interface = "127.0.0.1",
              port = port,
              connectionContext = HttpConnectionContext(http2 = Always))
        }
    }

    // report successful binding
    bindings.foreach { bs =>
      bs.foreach { binding =>
        println(s"gRPC server bound to: ${binding.localAddress}")
      }
    }

    bindings.foreach(_.foreach { binding =>
      println(s"gRPC server bound to: ${binding.localAddress}")
    })

    bindings
  }
}

//#full-server
