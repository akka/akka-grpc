/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.net.InetSocketAddress
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.ClientConnectionException
import akka.grpc.scaladsl.tools.MutableServiceDiscovery
import akka.http.scaladsl.Http
import akka.stream.{ Materializer, SystemMaterializer }
import com.typesafe.config.{ Config, ConfigFactory }
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
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class LoadBalancingIntegrationSpecNetty extends LoadBalancingIntegrationSpec("netty")

// TODO FIXME enable this test when we can use a pool interface in AkkaHttpClientUtils
// https://github.com/akka/akka-grpc/issues/1196
// https://github.com/akka/akka-grpc/issues/1197
//class LoadBalancingIntegrationSpecAkkaHttp
//    extends LoadBalancingIntegrationSpec("akka-http",
//      ConfigFactory.parseString("""akka.grpc.client."*".backend = "akka-http" """).withFallback(ConfigFactory.load()))

class LoadBalancingIntegrationSpec(backend: String, config: Config = ConfigFactory.load())
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {
  implicit val system: ActorSystem = ActorSystem("LoadBalancingIntegrationSpec", config)
  implicit val mat: Materializer = SystemMaterializer(system).materializer
  implicit val ec: ExecutionContext = system.dispatcher

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, Span(10, org.scalatest.time.Millis))

  "Client-side loadbalancing" should {
    "send requests to multiple endpoints" in {
      val service1 = new CountingGreeterServiceImpl()
      val service2 = new CountingGreeterServiceImpl()

      val server1 = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service1)).futureValue
      val server2 = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service2)).futureValue

      val discovery = MutableServiceDiscovery(List(server1, server2))
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          .withLoadBalancingPolicy("round_robin"))
      for (i <- 1 to 100) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      service1.greetings.get + service2.greetings.get should be(100)
      service1.greetings.get should be > 0
      service2.greetings.get should be > 0
    }

    "re-discover endpoints on failure" in {
      val service1materializer = Materializer(system)
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
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          .withLoadBalancingPolicy("round_robin"))

      val requestsPerServer = 2

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
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          .withLoadBalancingPolicy("round_robin"))

      for (i <- 1 to 100) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      service.greetings.get should be(100)
    }

    "fail when no valid endpoints are provided (don't retry) when max attempts is set to '1'" in {
      val discovery =
        new MutableServiceDiscovery(
          List(new InetSocketAddress("example.invalid", 80), new InetSocketAddress("example.invalid", 80)))
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          .withLoadBalancingPolicy("round_robin")
          // Low value to speed up the test
          .withConnectionAttempts(1))

      val failure =
        client.sayHello(HelloRequest(s"Hello friend")).failed.futureValue.asInstanceOf[StatusRuntimeException]
      // FIXME issue #1857, not sure how this is supposed to be
      if (backend == "netty")
        failure.getStatus.getCode should (equal(Code.UNKNOWN).or(equal(Code.UNAVAILABLE)))
      else
        failure.getStatus.getCode should be(Code.UNAVAILABLE)

      client.closed.failed.futureValue shouldBe a[ClientConnectionException]
    }

    "not fail when no valid endpoints are provided (retry indefinitely) when max attempts is set to another positive value" in {
      val discovery =
        new MutableServiceDiscovery(
          List(new InetSocketAddress("example.invalid", 80), new InetSocketAddress("example.invalid", 80)))
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          .withLoadBalancingPolicy("round_robin")
          // Low value to speed up the test
          .withConnectionAttempts(2))

      val failure =
        client.sayHello(HelloRequest(s"Hello friend")).failed.futureValue.asInstanceOf[StatusRuntimeException]
      // FIXME issue #1857, not sure how this is supposed to be
      if (backend == "netty")
        failure.getStatus.getCode should (equal(Code.UNKNOWN).or(equal(Code.UNAVAILABLE)))
      else
        failure.getStatus.getCode should be(Code.UNAVAILABLE)

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
