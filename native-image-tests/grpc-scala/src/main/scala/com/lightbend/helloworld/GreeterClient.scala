package com.lightbend.helloworld

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Failure
import scala.util.Success
import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.Source

object GreeterClient {

  def runTests()(implicit system: ActorSystem[_]): Future[Done] = {
    import system.executionContext

    val clientSettings = GrpcClientSettings.fromConfig("helloworld.GreeterService")
    println(s"Using client backend: ${clientSettings.backend}")
    val client = GreeterServiceClient(clientSettings)

    val names = List("Alice", "Bob")

    def singleRequestReply(name: String): Future[Done] = {
      println(s"Performing request: $name")
      val reply = client.sayHello(HelloRequest(name))
      reply.map { reply =>
        println(s"Saw unary reply $reply")
        Done
      }
    }

    def streamingBroadcast(name: String): Future[Done] = {
      println(s"Performing streaming requests: $name")

      val requestStream: Source[HelloRequest, NotUsed] =
        Source
          .tick(100.millis, 100.millis, "tick")
          .take(10)
          .zipWithIndex
          .map { case (_, i) => i }
          .map(i => HelloRequest(s"$name-$i"))
          .mapMaterializedValue(_ => NotUsed)

      val responseStream: Source[HelloReply, NotUsed] = client.sayHelloToAll(requestStream)
      val done: Future[Done] =
        responseStream.runForeach(reply => println(s"$name got streaming reply: ${reply.message}"))

      done.map { _ =>
        println("streamingBroadcast done")
        Done
      }
    }

    for {
      _ <- Future.sequence(names.map(singleRequestReply))
      _ <- Future.sequence(names.map(streamingBroadcast))
    } yield Done
  }

}
