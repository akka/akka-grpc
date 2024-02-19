package com.lightbend.helloworld

import scala.concurrent.Future

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.BroadcastHub
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.MergeHub
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

class GreeterServiceImpl(system: ActorSystem[_]) extends GreeterService {
  private implicit val sys: ActorSystem[_] = system

  override def sayHello(request: HelloRequest): Future[HelloReply] = {
    Future.successful(HelloReply(s"Hello, ${request.name}"))
  }

  override def sayHelloToAll(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = {
    in.map(request => HelloReply(s"Hello, ${request.name}"))
  }
}
