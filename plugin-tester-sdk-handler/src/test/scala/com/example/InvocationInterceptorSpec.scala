/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.InvocationInterceptor
import akka.grpc.scaladsl.GrpcExceptionHandler
import akka.http.javadsl.model.HttpRequest
import akka.http.scaladsl.Http
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Supplier
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters.CompletionStageOps

class InvocationInterceptorSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val system: ActorSystem[Nothing] = ActorSystem[Any](
    Behaviors.empty,
    "InvocationInterceptorSpec",
    ConfigFactory.parseString("akka.http.server.enable-http2 = on"))
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  private val currentValue = new ThreadLocal[String]

  // (method, thread, header value) for each intercepted call
  private val intercepted = new ConcurrentLinkedQueue[(String, String, String)]()

  private object TestInterceptor extends InvocationInterceptor {
    override def intercept[T](request: HttpRequest, method: String, call: Supplier[T]): T = {
      intercepted.add(
        (method, Thread.currentThread().getName, request.getHeader("x-test").map[String](_.value()).orElse("")))
      currentValue.set(s"intercepted-$method")
      try call.get()
      finally currentValue.remove()
    }
  }

  private final class GreeterServiceImpl extends GreeterService {
    private def reply() =
      HelloReply.newBuilder().setMessage(s"${currentValue.get()}|${Thread.currentThread().getName}").build()

    override def sayHello(in: HelloRequest): CompletionStage[HelloReply] =
      CompletableFuture.completedFuture(reply())

    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] =
      Source.single(reply())
  }

  private val handlerPf = new GreeterServiceScalaHandlerFactory().partialInstancePerRequest(
    _ => new GreeterServiceImpl,
    "",
    GrpcExceptionHandler.defaultMapper(system.classicSystem),
    system,
    akka.stream.Materializer(system))

  private def clientFor(
      handler: akka.http.scaladsl.model.HttpRequest => scala.concurrent.Future[akka.http.scaladsl.model.HttpResponse])
      : GreeterServiceClient = {
    val bound = Http().newServerAt("127.0.0.1", 0).bind(handler).futureValue
    GreeterServiceClient.create(
      GrpcClientSettings.connectToServiceAt("127.0.0.1", bound.localAddress.getPort).withTls(false),
      system)
  }

  private val interceptedClient =
    clientFor(request => handlerPf(request.addAttribute(InvocationInterceptor.AttributeKey, TestInterceptor)))
  private val plainClient = clientFor(handlerPf)

  private def parts(reply: HelloReply): (String, String) = {
    val Array(value, thread) = reply.getMessage.split('|')
    (value, thread)
  }

  "The SDK instance per request handler" should {

    "invoke unary methods through the interceptor from the request attribute" in {
      intercepted.clear()
      val reply =
        interceptedClient
          .sayHello()
          .addHeader("x-test", "unary")
          .invoke(HelloRequest.getDefaultInstance)
          .asScala
          .futureValue

      val (value, thread) = parts(reply)
      value shouldBe "intercepted-SayHello"
      intercepted.asScala.toList shouldBe List(("SayHello", thread, "unary"))
    }

    "invoke streaming response methods through the interceptor from the request attribute" in {
      intercepted.clear()
      val replies = interceptedClient
        .itKeepsReplying()
        .addHeader("x-test", "streaming")
        .invoke(HelloRequest.getDefaultInstance)
        .asScala
        .runWith(Sink.seq)
        .futureValue

      replies should have size 1
      val (value, thread) = parts(replies.head)
      value shouldBe "intercepted-ItKeepsReplying"
      intercepted.asScala.toList shouldBe List(("ItKeepsReplying", thread, "streaming"))
    }

    "invoke methods directly when there is no interceptor attribute" in {
      intercepted.clear()
      val reply = plainClient.sayHello(HelloRequest.getDefaultInstance).asScala.futureValue

      parts(reply)._1 shouldBe "null"
      intercepted.isEmpty shouldBe true
    }
  }

  override def afterAll(): Unit =
    ActorTestKit.shutdown(system)
}
