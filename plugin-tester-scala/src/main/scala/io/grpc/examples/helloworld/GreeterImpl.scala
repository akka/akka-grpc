package io.grpc.examples.helloworld

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source

class GreeterImpl extends Greeter {
  override def sayHello(in: HelloRequest): Future[HelloReply] = ???

  override def streamHellos(in: Source[HelloRequest, _]): Source[HelloReply, Any] = ???

  override def itKeepsTalking(in: Source[HelloRequest,_]): Future[HelloReply] = ???
}
