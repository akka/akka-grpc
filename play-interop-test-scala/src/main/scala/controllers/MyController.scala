/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import example.myapp.helloworld.grpc.helloworld.{ GreeterServiceClient, HelloRequest }
import javax.inject.{ Inject, Singleton }
import play.api.mvc.{ AbstractController, ControllerComponents }

import scala.concurrent.ExecutionContext

// #using-client
@Singleton
class MyController @Inject() (implicit greeterClient: GreeterServiceClient, cc: ControllerComponents, mat: Materializer, exec: ExecutionContext) extends AbstractController(cc) {

  def sayHello(name: String) = Action.async { implicit request =>
    greeterClient.sayHello(HelloRequest(name))
      .map { reply =>
        Ok(s"response: ${reply.message}")
      }
  }

}
// #using-client
