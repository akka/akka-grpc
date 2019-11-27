package example.myapp.helloworld

import _root_.grpc.reflection.v1alpha.reflection._

import com.typesafe.config._

import scala.concurrent._

import akka.actor._
import akka.stream._

import akka.grpc.scaladsl.ServiceHandler

import akka.http.scaladsl._
import akka.http.scaladsl.model._

import example.myapp.helloworld.grpc._
import akka.grpc.internal._

object Main extends App {
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    implicit val sys = ActorSystem("HelloWorld", conf)

    implicit val mat: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext = sys.dispatcher

    // Create service handlers
    val greeter: PartialFunction[HttpRequest, Future[HttpResponse]] =
      GreeterServiceHandler.partial(new GreeterServiceImpl())
    val reflection: PartialFunction[HttpRequest, Future[HttpResponse]] =
      ServerReflectionHandler.partial(ServerReflectionImpl(Seq.empty, List.empty))

    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(greeter, reflection)


    // Bind service handler servers to localhost:8080/8081
    val binding = Http().bindAndHandleAsync(
      service,
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())

    // report successful binding
    binding.foreach { binding =>
      println(s"gRPC server bound to: ${binding.localAddress}")
    }
}