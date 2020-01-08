package example.myapp.helloworld

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source

import example.myapp.helloworld.grpc._

class GreeterServiceImpl extends GreeterService {
  override def sayHello(in: HelloRequest): Future[HelloReply] = Future.successful(HelloReply(s"Hey ${in.name}"))

  override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???

  override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???

  override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = ???

}
