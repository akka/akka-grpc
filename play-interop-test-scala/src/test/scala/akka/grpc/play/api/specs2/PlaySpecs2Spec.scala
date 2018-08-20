/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play.api.specs2

import controllers.GreeterServiceImpl
import example.myapp.helloworld.grpc.helloworld.{ GreeterService, GreeterServiceClient, HelloRequest }
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{ WSClient, WSRequest }
import play.api.routing.Router
import play.api.test.{ NewWithServer, _ }

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Test for the Play gRPC Specs2 APIs
 */
@RunWith(classOf[JUnitRunner])
class PlaySpecs2Spec extends Specification with PlaySpecification {

  def fakeApplication(): Application = {
    GuiceApplicationBuilder()
      .overrides(bind[Router].to[GreeterServiceImpl])
      .build()
  }

  class WithGreeterServer extends NewWithServer(app = fakeApplication()) with ServerGrpcClient {
    // RICH: Hardcode a helper for now because too hard for now to work out how to make WSClient work with endpoints
    def wsUrl(path: String): WSRequest = {
      val ws = app.injector.instanceOf[WSClient]
      val url = implicitEndpoint.pathUrl(path)
      ws.url(url)
    }
  }

  "A Play server bound to a gRPC router" should {
    "give a 404 when routing a non-gRPC request" in new WithGreeterServer {
      val result = await(wsUrl("/").get)
      result.status must ===(404)
    }
    "give an Ok header (and hopefully a not implemented trailer) when routing a non-existent gRPC method" in new WithGreeterServer { // Maybe should be a 426
      val result = await(wsUrl(s"/${GreeterService.name}/FooBar").get)
      result.status must ===(200)
      // TODO: Test that trailer has a not implemented status
    }
    "give a 500 when routing an empty request to a gRPC method" in new WithGreeterServer { // Maybe should be a 426
      val result = await(wsUrl(s"/${GreeterService.name}/SayHello").get)
      result.status must ===(500)
    }
    "work with a gRPC client" in new WithGreeterServer {
      withGrpcClient[GreeterServiceClient] { client: GreeterServiceClient =>
        val reply = await(client.sayHello(HelloRequest("Alice")))
        reply.message must be("Hello, Alice!")
        Await.result(client.close(), Duration.Inf)
      }
    }
  }
}
