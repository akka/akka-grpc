/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import akka.NotUsed
import akka.stream.scaladsl.Source
import example.myapp.helloworld.grpc.helloworld._
import org.slf4j.LoggerFactory

class CountingGreeterServiceImpl extends GreeterService {

  val log = LoggerFactory.getLogger(classOf[CountingGreeterServiceImpl])
  var greetings = new AtomicInteger(0);

  def sayHello(in: HelloRequest): Future[HelloReply] = {
    val count = greetings.incrementAndGet()
    log.info("{}, counter: {}", in.name, count)
    Future.successful(HelloReply(s"Hi ${in.name}!"))
  }

  def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] =
    Source(List(HelloReply("First"), HelloReply("Second"))).mapMaterializedValue { m => println("XXX MAT YYY"); m }
  def itKeepsTalking(
      in: akka.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest, akka.NotUsed])
      : scala.concurrent.Future[example.myapp.helloworld.grpc.helloworld.HelloReply] = ???
  def streamHellos(in: akka.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest, akka.NotUsed])
      : akka.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloReply, akka.NotUsed] = ???

}
