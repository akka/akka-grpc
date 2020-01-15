package example.myapp.helloworld

import com.typesafe.config._

import scala.concurrent._

import akka.actor._
import akka.stream._

//#server-reflection
import akka.http.scaladsl._
import akka.http.scaladsl.model._

import akka.grpc.scaladsl.ServiceHandler
import akka.grpc.scaladsl.ServerReflection

import example.myapp.helloworld.grpc._

//#server-reflection


object Main extends App {
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    implicit val sys = ActorSystem("HelloWorld", conf)

    implicit val mat: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext = sys.dispatcher

    //#server-reflection
    // Create service handlers
    val greeter: PartialFunction[HttpRequest, Future[HttpResponse]] =
      GreeterServiceHandler.partial(new GreeterServiceImpl())
    val reflection: PartialFunction[HttpRequest, Future[HttpResponse]] =
      ServerReflection.partial(List(GreeterService))

    // Bind service handler servers to localhost:8080
    val binding = Http().bindAndHandleAsync(
      ServiceHandler.concatOrNotFound(greeter, reflection),
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())
    //#server-reflection

    // report successful binding
    binding.foreach { binding =>
      println(s"gRPC server bound to: ${binding.localAddress}")
    }
}