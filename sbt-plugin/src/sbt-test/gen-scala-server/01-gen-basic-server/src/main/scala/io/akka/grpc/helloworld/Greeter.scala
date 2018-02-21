package io.akka.grpc.helloworld

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source

import io.akka.grpc.helloworld._

class Greeter extends GreeterService {
  override def sayHello(in: HelloRequest): Future[HelloReply] = ???

  override def streamHellos(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???

  override def itKeepsTalking(in: Source[HelloRequest,NotUsed]): Future[HelloReply] = ???
}