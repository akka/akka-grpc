package io.akka.grpc

import scala.concurrent.Future

import io.grpc.examples.echo._

class EchoServiceImpl extends EchoService {
  def echo(in: EchoMessage): Future[EchoMessage] = Future.successful(in)
}