

package io.akka.grpc.helloworld

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source

trait GreeterService {

  def sayHello(in: _root_.io.akka.grpc.helloworld.HelloRequest): Future[_root_.io.akka.grpc.helloworld.HelloReply]

  def itKeepsTalking(in: Source[_root_.io.akka.grpc.helloworld.HelloRequest, NotUsed]): Future[_root_.io.akka.grpc.helloworld.HelloReply]

  def streamHellos(in: Source[_root_.io.akka.grpc.helloworld.HelloRequest, NotUsed]): Future[_root_.io.akka.grpc.helloworld.HelloReply]

}
object GreeterService {
  val name = "helloworld.Greeter"
}