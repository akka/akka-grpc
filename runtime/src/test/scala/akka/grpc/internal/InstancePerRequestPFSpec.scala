/*
 * Copyright (C) 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ActorSystem
import akka.http.javadsl.model.HttpEntity
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.testkit.TestKit
import akka.testkit.TestProbe
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionException

class InstancePerRequestPFSpec
    extends TestKit(ActorSystem("InstancePerRequestPFSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  "The instance per request partial function" should {

    "handle routing to a concrete gRPC service impl method" in {
      val probe = TestProbe()

      // Instantiation, this is normally done in the generated TestServiceScalaHandler class
      val pf = new InstancePerRequestPF[TestService](
        { _ =>
          probe.ref ! "constructed"
          new TestServiceImpl
        },
        "com.akka.grpc.TestService",
        Array(
          new InstancePerRequestPF.GrpcMethod[TestService](
            "Echo",
            { (instance, _, _, _, request) =>
              // Content negotiation/request serialization/deserialization to proto message normally generated in the
              // ScalaHandler template, here we fake it, not important for the test
              instance
                .echo(new TestService.PretendProtoMessage(
                  request.entity().asInstanceOf[HttpEntity.Strict].getData.utf8String))
                .thenApply(out => HttpResponse(entity = out.text))
            })),
        PartialFunction.empty,
        system)

      pf.isDefinedAt(HttpRequest(uri = "https://example.com/whatever")) shouldBe false
      // Note: since only one instance of a service name can live in the same server, the PF will accept
      //       any request for that service, for missing methods it fails the request with not implemented exception
      //       when handling instead.
      pf.isDefinedAt(HttpRequest(uri = "https://example.com/com.akka.grpc.TestService/whatever")) shouldBe true
      pf.isDefinedAt(HttpRequest(uri = "https://example.com/com.akka.grpc.TestService/Echo")) shouldBe true

      val notImplemented =
        pf.apply(HttpRequest(uri = "https://example.com/com.akka.grpc.TestService/whatever")).failed.futureValue
      notImplemented match {
        case ee: ExecutionException if ee.getCause.isInstanceOf[NotImplementedError] => // this is what we expect
        case other =>
          other.printStackTrace()
          fail("Unexpected exception")
      }
      probe.expectNoMessage() // not constructed when method does not exist

      val futureResponse =
        pf.apply(HttpRequest(uri = "https://example.com/com.akka.grpc.TestService/Echo", entity = "hello"))
      probe.expectMsg("constructed")
      val echoResponse = futureResponse.futureValue
      echoResponse.entity.asInstanceOf[HttpEntity.Strict].getData.utf8String should ===("hello")
    }

  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
