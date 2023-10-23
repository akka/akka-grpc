/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.tools.MutableServiceDiscovery
import akka.http.scaladsl.Http
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld._
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.ExecutionContext
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

import akka.grpc.internal.ClientConnectionException

class NonBalancingIntegrationSpecNetty extends NonBalancingIntegrationSpec("netty")
class NonBalancingIntegrationSpecAkkaHttp extends NonBalancingIntegrationSpec("akka-http")

class NonBalancingIntegrationSpec(backend: String)
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {
  implicit val system: ActorSystem = ActorSystem(
    s"NonBalancingIntegrationSpec-$backend",
    ConfigFactory.parseString(s"""akka.grpc.client."*".backend = "$backend" """).withFallback(ConfigFactory.load()))
  implicit val mat: Materializer = SystemMaterializer(system).materializer
  implicit val ec: ExecutionContext = system.dispatcher

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds, Span(10, org.scalatest.time.Millis))

  s"Using pick-first (non load balanced clients) - $backend" should {
    "send requests to a single endpoint" in {
      val service1 = new CountingGreeterServiceImpl()
      val service2 = new CountingGreeterServiceImpl()

      val server1 = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service1)).futureValue
      val server2 = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service2)).futureValue

      val discovery = MutableServiceDiscovery(List(server1, server2))
      val client = GreeterServiceClient(GrpcClientSettings.usingServiceDiscovery("greeter", discovery).withTls(false))

      val numberOfRequests = 100

      val requests = List.fill(numberOfRequests)(client.sayHello(HelloRequest(s"Hello")))

      Future.sequence(requests).futureValue

      service1.greetings.get + service2.greetings.get should be(numberOfRequests)
      service1.greetings.get should (be(0).or(be(numberOfRequests)))
      service2.greetings.get should (be(0).or(be(numberOfRequests)))
    }

    "send requests to a single endpoint that is restarted in the middle" in {
      val service1 = new CountingGreeterServiceImpl()

      val server1 = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service1)).futureValue

      val discovery = MutableServiceDiscovery(List(server1))
      val client = GreeterServiceClient(GrpcClientSettings.usingServiceDiscovery("greeter", discovery).withTls(false))

      val numberOfRequests = 100
      val requestsPerConnection = numberOfRequests / 2

      val requestsOnFirstConnection = List.fill(requestsPerConnection)(client.sayHello(HelloRequest(s"Hello")))

      Future.sequence(requestsOnFirstConnection).futureValue
      server1.terminate(5.seconds).futureValue
      // Give it some time to complete unbind (suspected to cause test flakyness)
      Thread.sleep(250)
      // And restart
      Http().newServerAt("127.0.0.1", server1.localAddress.getPort).bind(GreeterServiceHandler(service1)).futureValue

      val requestsOnSecondConnection = List.fill(requestsPerConnection)(client.sayHello(HelloRequest(s"Hello")))

      Future.sequence(requestsOnSecondConnection).futureValue(timeout(15.seconds))
      service1.greetings.get should be(numberOfRequests)
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

      server1.unbind().futureValue
      server1.terminate(hardDeadline = 3.seconds).futureValue
      // Unfortunately `terminate` currently only waits for the HTTP/1.1 connections
      // to terminate, https://github.com/akka/akka-http/issues/3580
      service1materializer.shutdown()
      Thread.sleep(5000)

      system.log.info("Sending requests to server 2")
      for (i <- 1 to requestsPerServer) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      service1.greetings.get + service2.greetings.get should be(2 * requestsPerServer)
      service1.greetings.get should be(requestsPerServer)
      service2.greetings.get should be(requestsPerServer)
    }

    "select the right endpoint among invalid ones" in {
      if (backend == "netty")
        pending // FIXME issue #1857

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
        println(s" Sending request [$i]")
        client.sayHello(HelloRequest(s"Hello $i")).map(_ => println(s" Received response number [$i]")).futureValue
      }

      service.greetings.get should be(100)
    }

    "eventually fail when no valid endpoints are provided" in {
      // https://github.com/akka/akka-grpc/issues/1246
      if (backend == "akka-http")
        cancel("The Akka HTTP backend doesn't fail when the persistent connection fails")

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
      // FIXME issue #1857, not sure how this is supposed to be
      if (backend == "netty")
        failure.getStatus.getCode should (equal(Code.UNKNOWN).or(equal(Code.UNAVAILABLE)))
      else {
        failure.getStatus.getCode should be(Code.UNAVAILABLE)
        client.closed.failed.futureValue shouldBe a[ClientConnectionException]
      }
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
