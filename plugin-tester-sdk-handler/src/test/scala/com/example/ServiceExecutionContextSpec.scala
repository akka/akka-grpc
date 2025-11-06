/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.GrpcExceptionHandler
import akka.http.scaladsl.Http
import akka.stream.ActorAttributes
import akka.stream.Materializer
import akka.stream.javadsl.Source
import akka.stream.scaladsl.Sink
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.GreeterService
import example.myapp.helloworld.grpc.GreeterServiceClient
import example.myapp.helloworld.grpc.GreeterServiceScalaHandlerFactory
import example.myapp.helloworld.grpc.HelloReply
import example.myapp.helloworld.grpc.HelloRequest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import scala.concurrent.duration.DurationInt
import scala.jdk.FutureConverters.CompletionStageOps

class ServiceExecutionContextSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val system: ActorSystem[Nothing] = ActorSystem[Any](
    Behaviors.empty,
    "ServiceExecutionContextSpec",
    ConfigFactory.parseString("""
      akka.http.server.enable-http2 = on
      custom-dispatcher = {
        type = "Dispatcher"
        executor = "fork-join-executor"
      }
      """))
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  private final class GreeterServiceImpl extends GreeterService {

    private def replyWithCurrentThreadName() =
      HelloReply.newBuilder().setMessage(Thread.currentThread().getName).build()

    override def sayHello(in: HelloRequest): CompletionStage[HelloReply] = {
      CompletableFuture.completedFuture(replyWithCurrentThreadName())
    }

    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
      // invokin thread
      Source
        .single(replyWithCurrentThreadName())
        .concat(
          Source
            .single(in)
            // thread name in the running stream (materialized by akka grpc)
            .map(_ => replyWithCurrentThreadName()))
    }
  }

  "The SDK instance per request handler" should {

    val customMaterializer = Materializer(system, ActorAttributes.dispatcher("custom-dispatcher"))

    val handler = new GreeterServiceScalaHandlerFactory()

    val handlerPf = handler.partialInstancePerRequest(
      _ => new GreeterServiceImpl,
      "",
      GrpcExceptionHandler.defaultMapper(system.classicSystem),
      system,
      customMaterializer)

    val bound = Http().newServerAt("127.0.0.1", 0).withMaterializer(customMaterializer).bind(handlerPf).futureValue
    val port = bound.localAddress.getPort

    val client =
      GreeterServiceClient.create(GrpcClientSettings.connectToServiceAt("127.0.0.1", port).withTls(false), system)

    "run unary requests on the specified execution context" in {
      val reply = client.sayHello(HelloRequest.getDefaultInstance).asScala.futureValue

      reply.getMessage should include("custom-dispatcher")
    }

    "run streamed responses and their streams on specified execution context" in {
      val replies = client.itKeepsReplying(HelloRequest.getDefaultInstance).asScala.runWith(Sink.seq).futureValue

      replies should have size 2
      replies(0).getMessage should include("custom-dispatcher")
      // Note: this is only true because we started http server with the materializer as well
      replies(1).getMessage should include("custom-dispatcher")
    }

  }

  override def afterAll(): Unit = {
    ActorTestKit.shutdown(system)
  }

}
