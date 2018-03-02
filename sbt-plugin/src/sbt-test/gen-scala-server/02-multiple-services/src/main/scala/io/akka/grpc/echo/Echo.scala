package io.akka.grpc.echo

import scala.concurrent.Future

class Echo extends EchoService {
  def echo(in: EchoMessage): Future[EchoMessage] = Future.successful(in)
}