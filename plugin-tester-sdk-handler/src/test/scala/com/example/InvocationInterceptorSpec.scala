/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.grpc.GrpcServiceException
import akka.grpc.internal.GrpcProtocolNative
import akka.grpc.internal.InvocationInterceptor
import akka.grpc.scaladsl.GrpcExceptionHandler
import akka.http.javadsl.model.{ HttpRequest => JHttpRequest }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.stream.javadsl.Source
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import io.grpc.Status
import example.myapp.helloworld.grpc.GreeterService
import example.myapp.helloworld.grpc.GreeterServiceClient
import example.myapp.helloworld.grpc.GreeterServiceScalaHandlerFactory
import example.myapp.helloworld.grpc.HelloReply
import example.myapp.helloworld.grpc.HelloRequest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Supplier
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.Try

object InvocationInterceptorSpec {
  final case class Reply(constructed: String, invoked: String, later: String, thread: String)
}

class InvocationInterceptorSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  import InvocationInterceptorSpec.Reply

  implicit val system: ActorSystem[Nothing] = ActorSystem[Any](
    Behaviors.empty,
    "InvocationInterceptorSpec",
    ConfigFactory.parseString("akka.http.server.enable-http2 = on"))
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, Span(100, Millis))

  private val NotIntercepted = "not-intercepted"
  private val currentValue = new ThreadLocal[String]
  private def current(): String = Option(currentValue.get()).getOrElse(NotIntercepted)

  // (method, thread, header value) for each intercepted call
  private val intercepted = new ConcurrentLinkedQueue[(String, String, String)]()

  private object TestInterceptor extends InvocationInterceptor {
    override def intercept[T](request: JHttpRequest, method: String, call: Supplier[T]): T = {
      intercepted.add(
        (method, Thread.currentThread().getName, request.getHeader("x-test").map[String](_.value()).orElse("")))
      currentValue.set(s"intercepted-$method")
      try call.get()
      finally currentValue.remove()
    }
  }

  /**
   * Replies with `constructed|invoked|later|thread`: the thread local value when the instance was created, when the
   * method was invoked, and in user code running after the method returned, plus the thread that invoked the method.
   */
  private final class GreeterServiceImpl extends GreeterService {
    private val constructedWith = current()

    private def reply(invokedWith: String, invokingThread: String, later: String): HelloReply =
      HelloReply.newBuilder().setMessage(s"$constructedWith|$invokedWith|$later|$invokingThread").build()

    override def sayHello(in: HelloRequest): CompletionStage[HelloReply] = {
      val invokedWith = current()
      val thread = Thread.currentThread().getName
      if (in.getName == "async")
        CompletableFuture.supplyAsync(() => reply(invokedWith, thread, current()))
      else
        CompletableFuture.completedFuture(reply(invokedWith, thread, ""))
    }

    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
      val invokedWith = current()
      val thread = Thread.currentThread().getName
      Source.single(in).map(_ => reply(invokedWith, thread, current()))
    }

    override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): CompletionStage[HelloReply] = {
      val invokedWith = current()
      val thread = Thread.currentThread().getName
      in.map(_ => current())
        .runWith(akka.stream.javadsl.Sink.seq[String], system)
        .thenApply(seen => reply(invokedWith, thread, seen.asScala.mkString(",")))
    }

    override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = {
      val invokedWith = current()
      val thread = Thread.currentThread().getName
      in.map(_ => reply(invokedWith, thread, current()))
    }
  }

  private def parse(reply: HelloReply): Reply = {
    val Array(constructed, invoked, later, thread) = reply.getMessage.split("\\|", -1)
    Reply(constructed, invoked, later, thread)
  }

  private val handlerPf = new GreeterServiceScalaHandlerFactory().partialInstancePerRequest(
    _ => new GreeterServiceImpl,
    "",
    GrpcExceptionHandler.defaultMapper(system.classicSystem),
    system,
    Materializer(system))

  private val bindings = new ConcurrentLinkedQueue[Http.ServerBinding]()
  private val clients = new ConcurrentLinkedQueue[GreeterServiceClient]()

  private def clientFor(handler: HttpRequest => Future[HttpResponse]): GreeterServiceClient = {
    val bound = Http().newServerAt("127.0.0.1", 0).bind(handler).futureValue
    bindings.add(bound)
    val client = GreeterServiceClient.create(
      GrpcClientSettings.connectToServiceAt("127.0.0.1", bound.localAddress.getPort).withTls(false),
      system)
    clients.add(client)
    client
  }

  private val interceptedClient =
    clientFor(request => handlerPf(request.addAttribute(InvocationInterceptor.Key, TestInterceptor)))
  private val plainClient = clientFor(handlerPf)

  private val twoRequests = Source.from(List(HelloRequest.getDefaultInstance, HelloRequest.getDefaultInstance).asJava)

  "The SDK instance per request handler" should {

    "invoke unary methods through the interceptor from the request attribute, creating the instance outside of it" in {
      intercepted.clear()
      val reply = parse(
        interceptedClient
          .sayHello()
          .addHeader("x-test", "unary")
          .invoke(HelloRequest.getDefaultInstance)
          .asScala
          .futureValue)

      reply.constructed shouldBe NotIntercepted
      reply.invoked shouldBe "intercepted-SayHello"
      intercepted.asScala.toList shouldBe List(("SayHello", reply.thread, "unary"))
    }

    "not cover the completion of a returned CompletionStage" in {
      intercepted.clear()
      val reply =
        parse(interceptedClient.sayHello(HelloRequest.newBuilder().setName("async").build()).asScala.futureValue)

      reply.invoked shouldBe "intercepted-SayHello"
      reply.later shouldBe NotIntercepted
      intercepted.asScala.map(_._1).toList shouldBe List("SayHello")
    }

    "invoke streaming response methods through the interceptor but not cover the returned stream" in {
      intercepted.clear()
      val replies = interceptedClient
        .itKeepsReplying()
        .addHeader("x-test", "streaming")
        .invoke(HelloRequest.getDefaultInstance)
        .asScala
        .runWith(Sink.seq)
        .futureValue
        .map(parse)

      replies should have size 1
      replies.head.constructed shouldBe NotIntercepted
      replies.head.invoked shouldBe "intercepted-ItKeepsReplying"
      replies.head.later shouldBe NotIntercepted
      intercepted.asScala.toList shouldBe List(("ItKeepsReplying", replies.head.thread, "streaming"))
    }

    "invoke streaming request methods through the interceptor but not cover the incoming stream" in {
      intercepted.clear()
      val reply = parse(
        interceptedClient
          .itKeepsTalking()
          .addHeader("x-test", "client-streaming")
          .invoke(twoRequests)
          .asScala
          .futureValue)

      reply.constructed shouldBe NotIntercepted
      reply.invoked shouldBe "intercepted-ItKeepsTalking"
      reply.later shouldBe s"$NotIntercepted,$NotIntercepted"
      intercepted.asScala.toList shouldBe List(("ItKeepsTalking", reply.thread, "client-streaming"))
    }

    "invoke bidirectional streaming methods through the interceptor but not cover the streams" in {
      intercepted.clear()
      val replies = interceptedClient
        .streamHellos()
        .addHeader("x-test", "bidi")
        .invoke(twoRequests)
        .asScala
        .runWith(Sink.seq)
        .futureValue
        .map(parse)

      replies should have size 2
      replies.map(_.constructed).distinct shouldBe Seq(NotIntercepted)
      replies.map(_.invoked).distinct shouldBe Seq("intercepted-StreamHellos")
      replies.map(_.later).distinct shouldBe Seq(NotIntercepted)
      intercepted.asScala.toList shouldBe List(("StreamHellos", replies.head.thread, "bidi"))
    }

    "create the instance before unmarshalling the request, so a denial wins over a malformed body" in {
      // the Akka runtime runs ACL and JWT checks in the factory
      val denyingPf = new GreeterServiceScalaHandlerFactory().partialInstancePerRequest(
        _ => throw new GrpcServiceException(Status.PERMISSION_DENIED),
        "",
        GrpcExceptionHandler.defaultMapper(system.classicSystem),
        system,
        Materializer(system))
      // gRPC frame header for a 3 byte message, followed by bytes that are not a valid protobuf message
      val malformedBody = ByteString(0, 0, 0, 0, 3, 0xff, 0xff, 0xff)
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = "/helloworld.GreeterService/SayHello",
        entity = HttpEntity(GrpcProtocolNative.contentType, malformedBody))

      val grpcStatus =
        Future
          .fromTry(Try(denyingPf(request.addAttribute(InvocationInterceptor.Key, TestInterceptor))))
          .flatten
          .map(_.headers.find(_.is("grpc-status")).map(_.value.toInt))(system.executionContext)
          .recover { case e: GrpcServiceException => Some(e.status.getCode.value) }(system.executionContext)
          .futureValue

      grpcStatus shouldBe Some(Status.Code.PERMISSION_DENIED.value)
    }

    "create the instance and invoke methods directly when there is no interceptor attribute" in {
      intercepted.clear()
      val reply = parse(plainClient.sayHello(HelloRequest.getDefaultInstance).asScala.futureValue)

      reply.constructed shouldBe NotIntercepted
      reply.invoked shouldBe NotIntercepted
      intercepted.isEmpty shouldBe true
    }
  }

  override def afterAll(): Unit = {
    clients.asScala.foreach(_.close().asScala.futureValue)
    bindings.asScala.foreach(_.unbind().futureValue)
    ActorTestKit.shutdown(system)
  }
}
