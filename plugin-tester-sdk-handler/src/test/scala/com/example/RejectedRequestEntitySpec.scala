/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.internal.GrpcProtocolNative
import akka.grpc.scaladsl.GrpcExceptionHandler
import akka.grpc.scaladsl.headers.`Message-Encoding`
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import akka.stream.javadsl.{ Source => JSource }
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import akka.util.ByteString
import example.myapp.helloworld.grpc.GreeterService
import example.myapp.helloworld.grpc.GreeterServiceScalaHandlerFactory
import example.myapp.helloworld.grpc.HelloReply
import example.myapp.helloworld.grpc.HelloRequest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.CompletionStage
import scala.collection.immutable
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt

class RejectedRequestEntitySpec
    extends TestKit(ActorSystem("RejectedRequestEntitySpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(3.seconds, Span(50, Millis))

  private final class UnusedGreeterService extends GreeterService {
    override def sayHello(in: HelloRequest): CompletionStage[HelloReply] = ???
    override def itKeepsReplying(in: HelloRequest): JSource[HelloReply, NotUsed] = ???
    override def itKeepsTalking(in: JSource[HelloRequest, NotUsed]): CompletionStage[HelloReply] = ???
    override def streamHellos(in: JSource[HelloRequest, NotUsed]): JSource[HelloReply, NotUsed] = ???
  }

  private val handler = new GreeterServiceScalaHandlerFactory().partialInstancePerRequest(
    _ => new UnusedGreeterService,
    "",
    GrpcExceptionHandler.defaultMapper(system),
    system,
    Materializer(system))

  private def expectEntityRead(contentType: ContentType.Binary, headers: immutable.Seq[HttpHeader]): Unit = {
    val read = Promise[Done]()
    val body = Source.single(ByteString("body")).map { bytes =>
      read.trySuccess(Done)
      bytes
    }

    handler(
      HttpRequest(
        HttpMethods.POST,
        "/helloworld.GreeterService/SayHello",
        headers,
        HttpEntity.Chunked.fromData(contentType, body)))

    read.future.futureValue shouldBe Done
  }

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "The SDK instance per request handler" should {
    "read the entity of a request with an unsupported content type" in {
      expectEntityRead(ContentTypes.`application/octet-stream`, Nil)
    }

    "read the entity of a request with an unsupported encoding" in {
      expectEntityRead(GrpcProtocolNative.contentType, `Message-Encoding`("unsupported") :: Nil)
    }
  }
}
