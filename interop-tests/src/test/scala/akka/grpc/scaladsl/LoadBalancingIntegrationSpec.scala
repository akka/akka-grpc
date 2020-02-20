/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.ActorSystem
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.stream.scaladsl.Source

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import example.myapp.helloworld.grpc.helloworld._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span

class CountingGreeterServiceImpl extends GreeterService {
  var greetings = new AtomicInteger(0);

  def sayHello(in: HelloRequest): Future[HelloReply] = {
    greetings.incrementAndGet()
    Future.successful(HelloReply(s"Hi ${in.name}!"))
  }

  def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = ???
  def itKeepsTalking(
      in: akka.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest, akka.NotUsed])
      : scala.concurrent.Future[example.myapp.helloworld.grpc.helloworld.HelloReply] = ???
  def streamHellos(in: akka.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest, akka.NotUsed])
      : akka.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloReply, akka.NotUsed] = ???

}

final class MutableServiceDiscovery(targets: List[Http.ServerBinding]) extends ServiceDiscovery {
  var services: Future[Resolved] = _

  setServices(targets)

  def setServices(targets: List[Http.ServerBinding]): Unit =
    services = Future.successful(
      Resolved(
        "greeter",
        targets.map(
          target =>
            ResolvedTarget(
              target.localAddress.getHostString,
              Some(target.localAddress.getPort),
              Some(target.localAddress.getAddress)))))

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    require(query.serviceName == "greeter")
    services
  }
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

      val discovery = new MutableServiceDiscovery(List(server1, server2))
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

      val discovery = new MutableServiceDiscovery(List(server1))
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          .withGrpcLoadBalancingType("round_robin"))

      val requestsPerServer = 2

      for (i <- 1 to requestsPerServer) {
        client.sayHello(HelloRequest(s"Hello $i")).futureValue
      }

      discovery.setServices(List(server2))

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
  }

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }
}
