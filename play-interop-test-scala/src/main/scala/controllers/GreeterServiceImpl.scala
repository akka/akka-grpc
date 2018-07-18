/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package controllers

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import example.myapp.helloworld.grpc.helloworld.{ GreeterService, HelloReply, HelloRequest }
import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

/** Would be written by the user, with support for dependency injection etc */
@Singleton
class GreeterServiceImpl @Inject() (implicit mat: Materializer) extends GreeterService {

  override def sayHello(in: HelloRequest): Future[HelloReply] = Future.successful(HelloReply(s"Hello, ${in.name}!"))

}
