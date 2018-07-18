/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package controllers

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import example.myapp.helloworld.grpc.helloworld.{ AbstractGreeterServiceRouter, HelloReply, HelloRequest }
import javax.inject.{ Inject, Singleton }
import play.api.inject.Injector

import scala.concurrent.Future

/** User implementation, with support for dependency injection etc */
@Singleton
class GreeterServiceImpl @Inject() (implicit mat: Materializer) extends AbstractGreeterServiceRouter(mat) {

  override def sayHello(in: HelloRequest): Future[HelloReply] = Future.successful(HelloReply(s"Hello, ${in.name}!"))

}
