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

class LoadBalancingIntegrationSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val system = ActorSystem("LoadBalancingIntegrationSpec")
  implicit val mat = akka.stream.ActorMaterializer.create(system)
  implicit val ec = system.dispatcher

  "Client-side loadbalancing" should {
    "send requests to multiple endpoints" in {
      val service1 = new CountingGreeterServiceImpl()
      val service2 = new CountingGreeterServiceImpl()

      val server1 = Http().bindAndHandleAsync(GreeterServiceHandler(service1), "127.0.0.1", 0).futureValue
      val server2 = Http().bindAndHandleAsync(GreeterServiceHandler(service2), "127.0.0.1", 0).futureValue

      val discovery = new ServiceDiscovery() {
        override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
          require(query.serviceName == "greeter")
          return Future.successful(
            Resolved(
              "greeter",
              List(
                ResolvedTarget(
                  server1.localAddress.getHostString(),
                  Some(server1.localAddress.getPort()),
                  Some(server1.localAddress.getAddress())),
                ResolvedTarget(
                  server2.localAddress.getHostString(),
                  Some(server2.localAddress.getPort()),
                  Some(server2.localAddress.getAddress())))))
        }
      }
      val client = GreeterServiceClient(
        GrpcClientSettings
          .usingServiceDiscovery("greeter", discovery)
          .withTls(false)
          .withGrpcLoadBalancingType("round_robin"))
      for (i <- 1 to 100) {
        Await.result(client.sayHello(HelloRequest(s"Hello $i")), 10.seconds)
      }

      service1.greetings.get + service2.greetings.get should be(100)
      service1.greetings.get should be > (0)
      service2.greetings.get should be > (0)
    }
  }

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }
}
