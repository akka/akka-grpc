package helloworld

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source

class GreeterServiceImpl extends GreeterService {
  override def sayHello(in: HelloRequest): Future[HelloReply] = ???

  override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???

  override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???

  override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = ???

}
