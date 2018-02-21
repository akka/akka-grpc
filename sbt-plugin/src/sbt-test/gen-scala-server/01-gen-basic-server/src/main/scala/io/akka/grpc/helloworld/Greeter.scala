package io.akka.grpc.helloworld

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source

class Greeter extends GreeterService {
  override def sayHello(in: HelloRequest): Future[HelloRequest] = ???

  override def streamHellos(in: Source[HelloRequest, NotUsed]): Future[HelloRequest] = ???

  override def itKeepsTalking(in: Source[HelloRequest,NotUsed]): Future[HelloRequest] = ???
}