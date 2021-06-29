/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.GrpcProtocol.TrailerFrame
import akka.grpc.GrpcResponseMetadata
import akka.grpc.internal.GrpcEntityHelpers
import akka.grpc.internal.GrpcProtocolNative
import akka.grpc.internal.GrpcResponseHelpers
import akka.grpc.internal.HeaderMetadataImpl
import akka.grpc.internal.Identity
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld.GreeterServiceClient
import example.myapp.helloworld.grpc.helloworld.GreeterServicePowerApiHandler
import example.myapp.helloworld.grpc.helloworld.HelloReply
import example.myapp.helloworld.grpc.helloworld.HelloRequest
import example.myapp.helloworld.grpc.helloworld._
import io.grpc.Status
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

class PowerApiSpecNetty extends PowerApiSpec("netty")
class PowerApiSpecAkkaHttp extends PowerApiSpec("akka-http")

abstract class PowerApiSpec(backend: String)
    extends TestKit(ActorSystem(
      "GrpcExceptionHandlerSpec",
      ConfigFactory.parseString(s"""akka.grpc.client."*".backend = "$backend" """).withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with Directives
    with BeforeAndAfter
    with BeforeAndAfterAll {

  override implicit val patienceConfig = PatienceConfig(5.seconds, Span(10, org.scalatest.time.Millis))

  val server =
    Http().newServerAt("localhost", 0).bind(GreeterServicePowerApiHandler(new PowerGreeterServiceImpl())).futureValue

  var client: GreeterServiceClient = _

  after {
    if (client != null && !client.closed.isCompleted) {
      client.close().futureValue
    }
  }
  override protected def afterAll(): Unit = {
    server.terminate(3.seconds)
    super.afterAll()
  }

  "The power API" should {
    "successfully pass metadata from client to server" in {
      client = GreeterServiceClient(
        GrpcClientSettings.connectToServiceAt("localhost", server.localAddress.getPort).withTls(false))

      client
        .sayHello()
        // No authentication
        .invoke(HelloRequest("Alice"))
        .futureValue
        .message should be("Hello, Alice (not authenticated)")

      client.sayHello().addHeader("Authorization", "foo").invoke(HelloRequest("Alice")).futureValue.message should be(
        "Hello, Alice (authenticated)")
    }

    "successfully pass metadata from server to client" in {
      implicit val serializer = GreeterService.Serializers.HelloReplySerializer
      val specialServer =
        Http()
          .newServerAt("localhost", 0)
          .bind(path(GreeterService.name / "SayHello") {
            implicit val writer = GrpcProtocolNative.newWriter(Identity)
            val trailingMetadata = new HeaderMetadataImpl(List(RawHeader("foo", "bar")))
            complete(
              GrpcResponseHelpers(
                Source.single(HelloReply("Hello there!")),
                trail = Source.single(GrpcEntityHelpers.trailer(Status.OK, trailingMetadata)))
                .addHeader(RawHeader("baz", "qux")))
          })
          .futureValue

      client = GreeterServiceClient(
        GrpcClientSettings.connectToServiceAt("localhost", specialServer.localAddress.getPort).withTls(false))

      val response = client
        .sayHello()
        // No authentication
        .invokeWithMetadata(HelloRequest("Alice"))
        .futureValue

      response.value.message should be("Hello there!")
      response.headers.getText("baz").get should be("qux")
      response.trailers.futureValue.getText("foo").get should be("bar")
    }

    "(on streamed calls) redeem the headers future as soon as they're available (and trailers future when trailers arrive)" in {

      // invoking streamed calls using the power API materializes a Future[GrpcResponseMetadata]
      // that should redeem as soon as the HEADERS is consumed. Then, the GrpcResponseMetadata instance
      // contains another Future that will redeem when receiving the trailers.
      client = GreeterServiceClient(
        GrpcClientSettings.connectToServiceAt("localhost", server.localAddress.getPort).withTls(false))

      val p = Promise[HelloRequest]()
      val requests: Source[HelloRequest, NotUsed] = Source.single(HelloRequest("Alice")).concat(Source.future(p.future))

      val responseSource: Source[HelloReply, Future[GrpcResponseMetadata]] =
        client.streamHellos().invokeWithMetadata(requests)

      val headers: Future[GrpcResponseMetadata] = responseSource.to(Sink.ignore).run()

      // blocks progress until redeeming `headers`
      val trailers = headers.futureValue.trailers

      // Don't send the finalization message until the headers future was redeemed (see above)
      trailers.isCompleted should be(false)
      p.trySuccess(HelloRequest("ByeBye"))

      trailers.futureValue // the trailers future eventually completes

    }

    "successfully pass metadata from server to client (for client-streaming calls)" in {
      val trailer = Promise[TrailerFrame]() // control the sending of the trailer

      implicit val serializer = GreeterService.Serializers.HelloReplySerializer
      val metadataServer =
        Http()
          .newServerAt("localhost", 0)
          .bind(path(GreeterService.name / "ItKeepsTalking") {
            implicit val writer = GrpcProtocolNative.newWriter(Identity)
            complete(
              GrpcResponseHelpers(Source.single(HelloReply("Hello there!")), trail = Source.future(trailer.future))
                .addHeader(RawHeader("foo", "bar")))
          })
          .futureValue

      client = GreeterServiceClient(
        GrpcClientSettings.connectToServiceAt("localhost", metadataServer.localAddress.getPort).withTls(false))

      val response = client.itKeepsTalking().invokeWithMetadata(Source.empty).futureValue

      response.value.message shouldBe "Hello there!"
      response.headers.getText("foo") shouldBe Some("bar")

      // only complete trailer after response received, to test reading of trailing headers
      trailer.success(GrpcEntityHelpers.trailer(Status.OK, new HeaderMetadataImpl(List(RawHeader("baz", "qux")))))

      response.trailers.futureValue.getText("baz") shouldBe Some("qux")

      metadataServer.terminate(3.seconds)
    }
  }

}
