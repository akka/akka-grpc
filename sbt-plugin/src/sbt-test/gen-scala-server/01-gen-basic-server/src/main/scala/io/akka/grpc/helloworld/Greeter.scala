package io.akka.grpc.helloworld

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source

class Greeter extends GreeterService {
  def sayHello(in: HelloRequest): Future[HelloRequest] = ???

  def streamHellos(in: Source[HelloRequest, NotUsed]): Future[HelloRequest] = ???

  def itKeepsTalking(in: Source[HelloRequest,NotUsed]): Future[HelloRequest] = ???
}