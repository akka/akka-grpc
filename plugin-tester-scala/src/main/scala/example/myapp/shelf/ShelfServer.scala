/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.shelf

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import com.typesafe.config.ConfigFactory
import example.myapp.shelf.grpc.{ Shelf, ShelfServiceHandler }

import scala.concurrent.{ ExecutionContext, Future }

object ShelfServer {
  def main(args: Array[String]): Unit = {
    val conf =
      ConfigFactory.parseString("akka.http.server.enable-http2 = on").withFallback(ConfigFactory.defaultApplication())
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty[Nothing], "shelfServerSystem", conf)
    implicit val ec: ExecutionContext = system.executionContext

    run(system).foreach(binding => println(s"gRPC HTTP transcoding server bound to: ${binding.localAddress}"))
  }

  def run(implicit system: ActorSystem[_]): Future[Http.ServerBinding] = {
    val shelfStorageActor = system.systemActorOf(KVStoreActor[Long, Shelf](), "shelfStorage")
    val service: HttpRequest => Future[HttpResponse] =
      ShelfServiceHandler.withHttpTranscoding(new ShelfServiceImpl(shelfStorageActor))
    Http().newServerAt("127.0.0.1", 8080).bind(service)
  }

}
