/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scalatestplus.play

import controllers.GreeterServiceImpl
import example.myapp.helloworld.grpc.helloworld.{ GreeterService, GreeterServiceClient, HelloRequest }
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.{ NewGuiceOneServerPerTest, PlaySpec }
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.routing.Router

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Test for the Play gRPC ScalaTest APIs
 */
class PlayScalaTestSpec extends PlaySpec with ServerGrpcClient
  with NewGuiceOneServerPerTest with ScalaFutures with IntegrationPatience {

  override def fakeApplication(): Application = {
    GuiceApplicationBuilder()
      .overrides(bind[Router].to[GreeterServiceImpl])
      .build()
  }

  implicit def ws: WSClient = app.injector.instanceOf(classOf[WSClient])

  "A Play server bound to a gRPC router" must {
    "give a 404 when routing a non-gRPC request" in { // Maybe should be a 426
      val result = wsUrl("/").get.futureValue
      result.status must be(404)
    }
    "give an Ok header (and hopefully a not implemented trailer) when routing a non-existent gRPC method" in { // Maybe should be a 426
      val result = wsUrl(s"/${GreeterService.name}/FooBar").get.futureValue
      result.status must be(200)
      // TODO: Test that trailer has a not implemented status
    }
    "give a 500 when routing an empty request to a gRPC method" in { // Maybe should be a 426
      val result = wsUrl(s"/${GreeterService.name}/SayHello").get.futureValue
      result.status must be(500)
    }
    "work with a gRPC client" in withGrpcClient[GreeterServiceClient] { client: GreeterServiceClient =>
      val reply = client.sayHello(HelloRequest("Alice")).futureValue
      reply.message must be("Hello, Alice!")
    }
  }
}
