/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

// #service-impl
package controllers

import akka.stream.Materializer
import example.myapp.helloworld.grpc.helloworld.{ AbstractGreeterServiceRouter, HelloReply, HelloRequest }
import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

/** User implementation, with support for dependency injection etc */
@Singleton
class GreeterServiceImpl @Inject() (implicit mat: Materializer) extends AbstractGreeterServiceRouter(mat) {

  override def sayHello(in: HelloRequest): Future[HelloReply] = Future.successful(HelloReply(s"Hello, ${in.name}!"))

}
// #service-impl
