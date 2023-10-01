package example.myapp.shelf

import akka.actor.{ ActorSystem, ClassicActorSystemProvider }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import com.typesafe.config.ConfigFactory
import example.myapp.shelf.grpc.ShelfServiceHandler

import scala.concurrent.Future

object ShelfServer {
  def main(args: Array[String]): Unit = {
    val conf =
      ConfigFactory.parseString("akka.http.server.enable-http2 = on").withFallback(ConfigFactory.defaultApplication())
    implicit val system: ClassicActorSystemProvider =
      ActorSystem("HelloWorld", conf).asInstanceOf[ClassicActorSystemProvider]
    new ShelfServer()
      .run()
      .foreach(binding => println(s"gRPC HTTP transcoding server bound to: ${binding.localAddress}"))(
        system.classicSystem.dispatcher)
  }
}
class ShelfServer(implicit system: ClassicActorSystemProvider) {
  def run(): Future[Http.ServerBinding] = {
    val service: HttpRequest => Future[HttpResponse] =
      ShelfServiceHandler.withHttpTranscoding(new ShelfServiceImpl())
    Http().newServerAt("127.0.0.1", 8080).bind(service)
  }
}
