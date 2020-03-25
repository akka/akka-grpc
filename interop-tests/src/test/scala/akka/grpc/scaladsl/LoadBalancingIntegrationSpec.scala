/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.net.InetSocketAddress

import io.grpc.Status.Code
import io.grpc.StatusRuntimeException

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.ClientConnectionException
import akka.http.scaladsl.Http

import example.myapp.helloworld.grpc.helloworld._

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span

final class MutableServiceDiscovery(targets: List[InetSocketAddress]) extends ServiceDiscovery {
  var services: Future[Resolved] = _

  setServices(targets)

  def setServices(targets: List[InetSocketAddress]): Unit =
    services = Future.successful(
      Resolved(
        "greeter",
        targets.map(target => ResolvedTarget(target.getHostString, Some(target.getPort), Some(target.getAddress)))))

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    require(query.serviceName == "greeter")
    services
  }
}
object MutableServiceDiscovery {
  def apply(targets: List[Http.ServerBinding]) = new MutableServiceDiscovery(targets.map(_.localAddress))
}

class LoadBalancingIntegrationSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val system = ActorSystem("LoadBalancingIntegrationSpec")
  implicit val mat = akka.stream.ActorMaterializer.create(system)
  implicit val ec = system.dispatcher

  override implicit val patienceConfig = PatienceConfig(5.seconds, Span(10, org.scalatest.time.Millis))

  "Client-side loadbalancing" should {
    "send requests to multiple endpoints" in {
      val service1 = new CountingGreeterServiceImpl()
      val service2 = new CountingGreeterServiceImpl()

      val server1 = Http().bindAndHandleAsync(GreeterServiceHandler(service1), "127.0.0.1", 0).futureValue
      val server2 = Http().bindAndHandleAsync(GreeterServiceHandler(service2), "127.0.0.1", 0).futureValue

      val discovery = MutableServiceDiscovery(List(server1, server2))
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          .withGrpcLoadBalancingType("round_robin"))
      for (i <- 1 to 100) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      service1.greetings.get + service2.greetings.get should be(100)
      service1.greetings.get should be > (0)
      service2.greetings.get should be > (0)
    }

    "re-discover endpoints on failure" in {
      val service1materializer = akka.stream.ActorMaterializer.create(system)
      val service1 = new CountingGreeterServiceImpl()
      val service2 = new CountingGreeterServiceImpl()

      val server1 =
        Http().bindAndHandleAsync(GreeterServiceHandler(service1), "127.0.0.1", 0)(service1materializer).futureValue
      val server2 = Http().bindAndHandleAsync(GreeterServiceHandler(service2), "127.0.0.1", 0).futureValue

      val discovery = MutableServiceDiscovery(List(server1))
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          .withGrpcLoadBalancingType("round_robin"))

      val requestsPerServer = 2

      for (i <- 1 to requestsPerServer) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      discovery.setServices(List(server2.localAddress))

      // This is rather heavy-handed, but surprisingly it seems just terminating
      // the binding isn't sufficient to actually abort the existing connection.
      server1.unbind().futureValue
      server1.terminate(hardDeadline = 100.milliseconds).futureValue
      service1materializer.shutdown()
      Thread.sleep(100)

      for (i <- 1 to requestsPerServer) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      service1.greetings.get + service2.greetings.get should be(2 * requestsPerServer)
      service1.greetings.get should be(requestsPerServer)
      service2.greetings.get should be(requestsPerServer)
    }

    "select the right endpoint among invalid ones" in {
      val service = new CountingGreeterServiceImpl()
      val server = Http().bindAndHandleAsync(GreeterServiceHandler(service), "127.0.0.1", 0).futureValue
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
          .withGrpcLoadBalancingType("round_robin"))

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
          .withGrpcLoadBalancingType("round_robin")
          // Low value to speed up the test
          .withConnectionAttempts(2))

      val failure =
        client.sayHello(HelloRequest(s"Hello friend")).failed.futureValue.asInstanceOf[StatusRuntimeException]
      failure.getStatus.getCode should be(Code.UNAVAILABLE)
      client.closed.failed.futureValue shouldBe a[ClientConnectionException]
    }
  }

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }
}
