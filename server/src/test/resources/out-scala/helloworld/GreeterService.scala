package helloworld

import scala.concurrent.Future

import akka.stream.scaladsl.Source

// include info from which proto file
trait GreeterService {

  // sends a greeting
  def sayHello(request: HelloRequest): Future[HelloReply]

  // streams a greeting
  def streamHellos(request: HelloRequest): Source[HelloReply]

  // streaming incoming
  def itKeepsTalking(requests: Source[HelloRequest]): HelloReply

  // a conversation
  def converse(requests: Source[HelloRequest]): Source[HelloReply]


  // akka typed proposal

  def sayHelloRef: ActorRef[HelloRequest]

}
