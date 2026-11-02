/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.util.Collections

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.jdk.FutureConverters._

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.internal.GrpcProtocolNative
import akka.grpc.javadsl.{ WebHandler => JWebHandler }
import akka.grpc.scaladsl.headers.`Message-Encoding`
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import akka.util.ByteString
import example.myapp.helloworld.grpc.ExceptionGreeterServiceImpl
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory
import example.myapp.helloworld.grpc.helloworld.GreeterServiceHandler
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

class RejectedRequestEntitySpec
    extends TestKit(ActorSystem("RejectedRequestEntitySpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(3.seconds, Span(50, Millis))

  private val method = "/helloworld.GreeterService/SayHello"
  private val unknownMethod = "/helloworld.GreeterService/Unknown"
  private val invalidPath = "/helloworld.GreeterService/SayHello/extra"
  private val unknownService = "/other.Service/SayHello"
  private val unsupportedEncoding = `Message-Encoding`("unsupported")

  private val scalaHandler = GreeterServiceHandler(new CountingGreeterServiceImpl)
  private val javaHandler = GreeterServiceHandlerFactory.create(new ExceptionGreeterServiceImpl, system)

  /** Sends a request to the handler and verifies that the handler reads the request entity. */
  private def expectEntityRead(handler: HttpRequest => Future[_])(
      path: String,
      contentType: ContentType.Binary,
      headers: immutable.Seq[HttpHeader]): Unit = {
    val read = Promise[Done]()
    val body = Source.single(ByteString("body")).mapMaterializedValue { _ =>
      read.trySuccess(Done)
      NotUsed
    }
    val request =
      HttpRequest(HttpMethods.POST, path, headers, HttpEntity.Chunked.fromData(contentType, body))

    handler(request)
    read.future.futureValue shouldBe Done
  }

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "The generated Scala handler" should {
    val expectRead = expectEntityRead(scalaHandler) _

    "read the entity of a request for an unknown method" in {
      expectRead(unknownMethod, GrpcProtocolNative.contentType, Nil)
    }

    "read the entity of a request with an unsupported content type" in {
      expectRead(method, ContentTypes.`application/octet-stream`, Nil)
    }

    "read the entity of a request with an unsupported encoding" in {
      expectRead(method, GrpcProtocolNative.contentType, unsupportedEncoding :: Nil)
    }

    "read the entity of a request with an invalid path" in {
      expectRead(invalidPath, GrpcProtocolNative.contentType, Nil)
    }

    "read the entity of a request for an unknown service" in {
      expectRead(unknownService, GrpcProtocolNative.contentType, Nil)
    }
  }

  "The generated Java handler" should {
    val expectRead = expectEntityRead(request => javaHandler(request).asScala) _

    "read the entity of a request for an unknown method" in {
      expectRead(unknownMethod, GrpcProtocolNative.contentType, Nil)
    }

    "read the entity of a request with an unsupported content type" in {
      expectRead(method, ContentTypes.`application/octet-stream`, Nil)
    }

    "read the entity of a request with an unsupported encoding" in {
      expectRead(method, GrpcProtocolNative.contentType, unsupportedEncoding :: Nil)
    }
  }

  "The Java gRPC-Web handler" should {
    "read the entity of a request that is not a gRPC-Web request" in {
      val webHandler = JWebHandler.grpcWebHandler(Collections.singletonList(javaHandler), system, Materializer(system))

      expectEntityRead(request => webHandler(request).asScala)(method, GrpcProtocolNative.contentType, Nil)
    }
  }
}
