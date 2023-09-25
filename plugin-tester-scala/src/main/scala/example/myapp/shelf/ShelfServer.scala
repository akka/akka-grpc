package example.myapp.shelf

import akka.actor.{ ActorSystem, ClassicActorSystemProvider }
import akka.grpc.scaladsl.ServiceHandler
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
    new ShelfServer().run()
  }
}
class ShelfServer(implicit system: ClassicActorSystemProvider) {
  def run(): Future[Http.ServerBinding] = {
    val grpcHandler = ShelfServiceHandler.partial(new ShelfServiceImpl())
    val httpTranscodingHandler = ShelfServiceHandler.partialHttpTranscoding(grpcHandler)

    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(httpTranscodingHandler, grpcHandler)

    Http().newServerAt("127.0.0.1", 8080).bind(service)
  }
}
