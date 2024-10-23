/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import akka.Done
import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import example.myapp.helloworld.grpc.GreeterService
import example.myapp.helloworld.grpc.GreeterServiceClient
import example.myapp.helloworld.grpc.GreeterServiceHandler
import example.myapp.helloworld.grpc.HelloReply
import example.myapp.helloworld.grpc.HelloRequest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

class AkkaHttpClientCancelSpec
    extends ScalaTestWithActorTestKit("""
        akka.http.server.enable-http2 = true
        akka.grpc.client."*" {
          backend = "akka-http"
          use-tls = false
        }
        """)
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  "The Akka HTTP client" should {

    "correctly cancel a server streaming call" in {
      val probe = createTestProbe[Any]()
      implicit val ec: ExecutionContext = system.executionContext

      val handler = GreeterServiceHandler(new GreeterService {
        override def sayHello(in: HelloRequest): Future[HelloReply] = ???
        override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???
        override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???

        override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
          Source
            .single(HelloReply.defaultInstance)
            // keep the stream alive indefinitely
            .concat(Source.maybe[HelloReply])
            // tell probe when we start and when we complete
            .watchTermination() { (_, termination) =>
              probe.ref ! "started"
              termination.onComplete { t =>
                probe.ref ! t
              }
              NotUsed
            }
        }

      })

      val binding =
        Http().newServerAt("127.0.0.1", 0).bind(handler).futureValue

      val client =
        GreeterServiceClient(GrpcClientSettings.connectToServiceAt("127.0.0.1", binding.localAddress.getPort))
      client.itKeepsReplying(HelloRequest.defaultInstance).runWith(Sink.head)

      probe.expectMessage("started")
      probe.expectMessage(5.seconds, Success(Done))
    }

  }

}
