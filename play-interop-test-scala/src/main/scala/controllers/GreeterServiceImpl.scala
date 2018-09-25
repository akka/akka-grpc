/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

// #service-impl
package controllers

import akka.stream.Materializer
import example.myapp.helloworld.grpc.helloworld.{ GreeterService, HelloReply, HelloRequest }
import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

/** User implementation, with support for dependency injection etc */
@Singleton
class GreeterServiceImpl @Inject() (implicit mat: Materializer /* param not needed in this example */ ) extends GreeterService {

  override def sayHello(in: HelloRequest): Future[HelloReply] = Future.successful(HelloReply(s"Hello, ${in.name}!"))

}
// #service-impl