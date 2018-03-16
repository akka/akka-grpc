package io.akka.grpc

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source

class GreeterImpl extends Greeter {
  override def sayHello(in: HelloRequest): Future[HelloReply] = ???

  override def streamHellos(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???

  override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???

  override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = ???

}
