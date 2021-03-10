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
    // Create service handler with a fallback to a Server Reflection handler.
    // `.withServerReflection` is a convenience method that contacts a partial
    // function of the provided service with a reflection handler for that
    // same service.
    val greeter: HttpRequest => Future[HttpResponse] =
      GreeterServiceHandler.withServerReflection(new GreeterServiceImpl())

    // Bind service handler servers to localhost:8080
    val binding = Http().bindAndHandleAsync(
      greeter,
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())
    //#server-reflection

    // report successful binding
    binding.foreach { binding =>
      println(s"gRPC server bound to: ${binding.localAddress}")
    }

    //#server-reflection-manual-concat
    // Create service handlers
    val greeterPartial: PartialFunction[HttpRequest, Future[HttpResponse]] =
      GreeterServiceHandler.partial(new GreeterServiceImpl(), "greeting-prefix")
    val echoPartial: PartialFunction[HttpRequest, Future[HttpResponse]] =
      EchoServiceHandler.partial(new EchoServiceImpl())
    // Create the reflection handler for multiple services
    val reflection =
      ServerReflection.partial(List(GreeterService, EchoService))

    // Concatenate the partial functions into a single handler
    val handler =
      ServiceHandler.concatOrNotFound(
        greeterPartial,
        echoPartial,
        reflection),
    //#server-reflection-manual-concat

}