/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.{ GrpcEntityHelpers, GrpcProtocolNative, GrpcResponseHelpers, HeaderMetadataImpl, Identity }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import example.myapp.helloworld.grpc.helloworld._
import io.grpc.Status
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

class PowerApiSpecNetty extends PowerApiSpec("netty")
class PowerApiSpecAkkaHttp extends PowerApiSpec("akka-http")

class PowerApiSpec(backend: String)
    extends TestKit(ActorSystem(
      "GrpcExceptionHandlerSpec",
      ConfigFactory.parseString(s"""akka.grpc.client."*".backend = "$backend" """).withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with Directives {

  override implicit val patienceConfig = PatienceConfig(5.seconds, Span(10, org.scalatest.time.Millis))

  val server =
    Http().newServerAt("localhost", 0).bind(GreeterServicePowerApiHandler(new PowerGreeterServiceImpl())).futureValue

  "The power API" should {
    "successfully pass metadata from client to server" in {
      val client = GreeterServiceClient(
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

      val client = GreeterServiceClient(
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
  }
}
