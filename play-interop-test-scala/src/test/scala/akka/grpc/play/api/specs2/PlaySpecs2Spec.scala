/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play.api.specs2

import controllers.GreeterServiceImpl
import example.myapp.helloworld.grpc.helloworld.{ GreeterService, GreeterServiceClient, HelloRequest }
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{ WSClient, WSRequest }
import play.api.routing.Router
import play.api.test._

/**
 * Test for the Play gRPC Specs2 APIs
 */
@RunWith(classOf[JUnitRunner])
class PlaySpecs2Spec extends NewForServer with ServerGrpcClient with PlaySpecification with ApplicationFactories {

  protected def applicationFactory: ApplicationFactory =
    appFromGuice(GuiceApplicationBuilder().overrides(bind[Router].to[GreeterServiceImpl]))

  // RICH: Still need to work out how to make WSClient work properly with endpoints
  def wsUrl(path: String)(implicit running: RunningServer): WSRequest = {
    val ws = running.app.injector.instanceOf[WSClient]
    val url = running.endpoints.httpEndpoint.get.pathUrl(path)
    ws.url(url)
  }

  "A Play server bound to a gRPC router" should {
    "give a 404 when routing a non-gRPC request" >> { implicit rs: RunningServer =>
      val result = await(wsUrl("/").get)
      result.status must ===(404)
    }
    "give an Ok header (and hopefully a not implemented trailer) when routing a non-existent gRPC method" >> { implicit rs: RunningServer =>
      val result = await(wsUrl(s"/${GreeterService.name}/FooBar").get)
      result.status must ===(200)
      // TODO: Test that trailer has a not implemented status
    }
    "give a 500 when routing an empty request to a gRPC method" >> { implicit rs: RunningServer =>
      val result = await(wsUrl(s"/${GreeterService.name}/SayHello").get)
      result.status must ===(500)
    }
    "work with a gRPC client" >> { implicit rs: RunningServer =>
      withGrpcClient[GreeterServiceClient] { client: GreeterServiceClient =>
        val reply = await(client.sayHello(HelloRequest("Alice")))
        reply.message must ===("Hello, Alice!")
      }
    }
  }
}
