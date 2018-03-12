package io.akka.grpc.echo

import scala.concurrent.Future

class EchoImpl extends Echo {
  def echo(in: EchoMessage): Future[EchoMessage] = Future.successful(in)
}