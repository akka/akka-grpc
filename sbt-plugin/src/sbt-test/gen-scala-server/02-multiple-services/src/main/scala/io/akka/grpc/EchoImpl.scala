package io.akka.grpc

import scala.concurrent.Future

class EchoImpl extends Echo {
  def echo(in: EchoMessage): Future[EchoMessage] = Future.successful(in)
}