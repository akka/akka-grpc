package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.internal.{ GrpcProtocolNative, GrpcRequestHelpers, Identity, TelemetryExtension, TelemetrySpi }
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld.{ GreeterService, GreeterServiceHandler, HelloRequest }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class TelemetrySpec
    extends TestKit(
      ActorSystem(
        "TelemetrySpec",
        ConfigFactory
          .parseString(s"""akka.grpc.telemetry-class = "akka.grpc.scaladsl.CollectingTelemetrySpi" """)
          .withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {
  "The client-side telemetry hook" should {
    "pick up matched requests" in {
      val handler = GreeterServiceHandler(new CountingGreeterServiceImpl)
      implicit val ser = GreeterService.Serializers.HelloRequestSerializer
      implicit val writer = GrpcProtocolNative.newWriter(Identity)
      handler(
        GrpcRequestHelpers(
          s"https://localhost/${GreeterService.name}/SayHello",
          Nil,
          Source.single(HelloRequest("Joe")))).futureValue

      val spi = TelemetryExtension(system).spi.asInstanceOf[CollectingTelemetrySpi]
      spi.requests.size should be(1)
      val (prefix, method, request) = spi.requests(0)
      prefix should be(GreeterService.name)
      method should be("SayHello")
      request.entity.contentType should be(GrpcProtocolNative.contentType)
    }
  }
}

class CollectingTelemetrySpi extends TelemetrySpi {
  var requests: List[(String, String, HttpRequest)] = Nil

  override def onRequest(prefix: String, method: String, request: HttpRequest): Unit =
    requests :+= (prefix, method, request)
}
