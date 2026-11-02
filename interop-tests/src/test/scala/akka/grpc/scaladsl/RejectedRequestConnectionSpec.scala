/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.FutureConverters._

import akka.actor.ActorSystem
import akka.grpc.internal.GrpcProtocolNative
import akka.grpc.javadsl.{ ServiceHandler => JServiceHandler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.ExceptionGreeterServiceImpl
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory
import example.myapp.helloworld.grpc.helloworld.GreeterService
import example.myapp.helloworld.grpc.helloworld.GreeterServiceHandler
import example.myapp.helloworld.grpc.helloworld.HelloRequest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

class RejectedRequestConnectionSpec
    extends TestKit(
      ActorSystem(
        "RejectedRequestConnectionSpec",
        ConfigFactory
          .parseString("""
            akka.http.server.http2 {
              incoming-connection-level-buffer-size = 256kB
              incoming-stream-level-buffer-size = 64kB
            }
            """)
          .withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(10.seconds, Span(50, Millis))
  import system.dispatcher

  private val sayHello = "/helloworld.GreeterService/SayHello"
  private val unknownMethod = "/helloworld.GreeterService/Unknown"
  private val unknownService = "/other.Service/SayHello"

  private val validBody = {
    val message = GreeterService.Serializers.HelloRequestSerializer.serialize(HelloRequest("Alice"))
    HttpEntity(GrpcProtocolNative.contentType, ByteString(0, 0, 0, 0, message.length) ++ message)
  }

  // sent slowly, so that the client does not stop sending before the data reaches the server
  private def rejectedBody =
    HttpEntity.Chunked.fromData(
      GrpcProtocolNative.contentType,
      Source(Vector.fill(16)(ByteString(new Array[Byte](8 * 1024)))).throttle(1, 20.millis))

  /** Rejected requests must not use up the connection window that the valid request needs. */
  private def expectValidRequestAnswered(path: String, handler: HttpRequest => Future[HttpResponse]): Unit = {
    val binding = Http().newServerAt("127.0.0.1", 0).bind(handler).futureValue
    val connection =
      Http().connectionTo("127.0.0.1").toPort(binding.localAddress.getPort).http2WithPriorKnowledge()
    val (requests, responses) = Source.queue[HttpRequest](16).via(connection).toMat(Sink.queue())(Keep.both).run()

    try {
      (1 to 8).foreach { _ =>
        requests.offer(HttpRequest(HttpMethods.POST, path, entity = rejectedBody))
        responses.pull().futureValue.get.discardEntityBytes()
      }
      requests.offer(HttpRequest(HttpMethods.POST, sayHello, entity = validBody))
      responses.pull().futureValue.get.status shouldBe StatusCodes.OK
    } finally {
      requests.complete()
      binding.terminate(1.second).futureValue
    }
  }

  private def scalaPartial = GreeterServiceHandler.partial(new CountingGreeterServiceImpl)

  private def javaHandler: HttpRequest => Future[HttpResponse] = {
    val handler = JServiceHandler.concatOrNotFound(
      system,
      GreeterServiceHandlerFactory.create(new ExceptionGreeterServiceImpl, system))
    request => handler(request).asScala.map(_.asInstanceOf[HttpResponse])
  }

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A connection" should {
    "keep working after requests for an unknown method" in {
      expectValidRequestAnswered(unknownMethod, GreeterServiceHandler(new CountingGreeterServiceImpl))
    }

    "keep working after requests for an unknown service with the Scala concatOrNotFound" in {
      expectValidRequestAnswered(unknownService, ServiceHandler.concatOrNotFound(system, scalaPartial))
    }

    "keep working after requests for an unknown service with the Java concatOrNotFound" in {
      expectValidRequestAnswered(unknownService, javaHandler)
    }
  }
}
