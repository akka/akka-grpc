/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.ClientConnectionException
import akka.grpc.scaladsl.tools.MutableServiceDiscovery
import akka.http.scaladsl.Http
import akka.stream.SystemMaterializer
import example.myapp.helloworld.grpc.helloworld._
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class NonBalancingIntegrationSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val system = ActorSystem("NonBalancingIntegrationSpec")
  implicit val mat = SystemMaterializer(system).materializer
  implicit val ec = system.dispatcher

  override implicit val patienceConfig = PatienceConfig(5.seconds, Span(10, org.scalatest.time.Millis))

  "Using pick-first (non load balanced clients)" should {
    "send requests to a single endpoint" in {
      val service1 = new CountingGreeterServiceImpl()
      val service2 = new CountingGreeterServiceImpl()

      val server1 = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service1)).futureValue
      val server2 = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service2)).futureValue

      val discovery = MutableServiceDiscovery(List(server1, server2))
      val client = GreeterServiceClient(GrpcClientSettings.usingServiceDiscovery("greeter", discovery).withTls(false))
      for (i <- 1 to 100) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      service1.greetings.get + service2.greetings.get should be(100)
      service1.greetings.get should be(100)
      service2.greetings.get should be(0)
    }

    "re-discover endpoints on failure" in {
      system.log.info("Starting test: re-discover endpoints on failure")
      val service1materializer = SystemMaterializer(system).createAdditionalSystemMaterializer()
      val service1 = new CountingGreeterServiceImpl()
      val service2 = new CountingGreeterServiceImpl()

      val server1 =
        Http()
          .newServerAt("127.0.0.1", 0)
          .withMaterializer(service1materializer)
          .bind(GreeterServiceHandler(service1))
          .futureValue
      val server2 = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service2)).futureValue

      val discovery = MutableServiceDiscovery(List(server1))
      val client = GreeterServiceClient(GrpcClientSettings.usingServiceDiscovery("greeter", discovery).withTls(false))

      val requestsPerServer = 2

      system.log.info("Sending requests to server 1")
      for (i <- 1 to requestsPerServer) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      discovery.setServices(List(server2.localAddress))

      // This is rather heavy-handed, but surprisingly it seems just terminating
      // the binding isn't sufficient to actually abort the existing connection.
      server1.unbind().futureValue
      server1.terminate(hardDeadline = 5.seconds).futureValue
      service1materializer.shutdown()

      system.log.info("Sending requests to server 2")
      for (i <- 1 to requestsPerServer) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      service1.greetings.get + service2.greetings.get should be(2 * requestsPerServer)
      service1.greetings.get should be(requestsPerServer)
      service2.greetings.get should be(requestsPerServer)
    }

    "select the right endpoint among invalid ones" in {
      val service = new CountingGreeterServiceImpl()
      val server = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service)).futureValue
      val discovery =
        new MutableServiceDiscovery(
          List(
            new InetSocketAddress("example.invalid", 80),
            server.localAddress,
            new InetSocketAddress("example.invalid", 80)))
      val client = GreeterServiceClient(GrpcClientSettings.usingServiceDiscovery("greeter", discovery).withTls(false))

      for (i <- 1 to 100) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      service.greetings.get should be(100)
    }

    "eventually fail when no valid endpoints are provided" in {
      val discovery =
        new MutableServiceDiscovery(
          List(new InetSocketAddress("example.invalid", 80), new InetSocketAddress("example.invalid", 80)))
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          // Low value to speed up the test
          .withConnectionAttempts(2))

      val failure =
        client.sayHello(HelloRequest(s"Hello friend")).failed.futureValue.asInstanceOf[StatusRuntimeException]
      failure.getStatus.getCode should be(Code.UNAVAILABLE)
      client.closed.failed.futureValue shouldBe a[ClientConnectionException]
    }

    "not fail when no valid endpoints are provided but no limit on attempts is set" in {
      val discovery =
        new MutableServiceDiscovery(
          List(new InetSocketAddress("example.invalid", 80), new InetSocketAddress("example.invalid", 80)))
      val client = GreeterServiceClient(GrpcClientSettings.usingServiceDiscovery("greeter", discovery).withTls(false))

      try {
        client.closed.failed.futureValue
        // Yes, we actually expect the future to timeout!
        fail("The `client.closed`future should not have completed. A Timeout was expected instead.")
      } catch {
        case _: TestFailedException => // that's what we're hoping for.
      }
    }

  }

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }
}
