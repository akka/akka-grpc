/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.{ GrpcClientSettings, GrpcServiceException }
import akka.http.scaladsl.model.HttpResponse
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld.{ GreeterServiceClient, HelloRequest }
import io.grpc.Status
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }

class AkkaHttpClientConnectionFailSpec
    extends TestKit(
      ActorSystem(
        "GrpcExceptionHandlerSpec",
        ConfigFactory
          .parseString("""
           akka.grpc.client."*".backend = "akka-http"
          akka.http.client.http2.max-persistent-attempts = 2
        """.stripMargin)
          .withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  "The Akka HTTP client backend" should {
    "fail queued requests when connection fails" in {

      // Note that the Akka HTTP client does not strictly adhere to the gRPC backoff protocol but has its own
      // backoff algorithm
      val client = GreeterServiceClient(GrpcClientSettings.connectToServiceAt("127.0.0.1", 5).withTls(false))

      val futures = (1 to 10).map { _ =>
        client.sayHello(HelloRequest())
      }
      // all should be failed
      import system.dispatcher
      val lifted = Future.sequence(futures.map(_.map(Success(_)).recover {
        case th: Throwable => Failure[HttpResponse](th)
      }))
      val results = lifted.futureValue(timeout(5.seconds))
      forAll(results) { it =>
        it.isFailure should be(true)
        it.failed.get match {
          case ex: GrpcServiceException =>
            ex.status.getCode shouldBe (Status.Code.UNAVAILABLE)
          case unexpected =>
            unexpected.printStackTrace()
            fail(s"Exception ${unexpected} was not a GrpcServiceException")
        }
      }
    }
  }

}
