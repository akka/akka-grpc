package example.myapp.helloworld

import scala.concurrent.Future

import example.myapp.helloworld.grpc._

class EchoServiceImpl extends EchoService {
  override def echo(in: HelloRequest): Future[HelloRequest] = Future.successful(in)
}
