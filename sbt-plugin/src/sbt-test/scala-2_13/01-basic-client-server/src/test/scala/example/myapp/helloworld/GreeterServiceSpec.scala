package example.myapp.helloworld;

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http

import akka.grpc.GrpcClientSettings

import example.myapp.helloworld.grpc._

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Span, Millis, Seconds }
import org.scalatest.wordspec.AnyWordSpec

class GreeterServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {
  implicit val system = ActorSystem("GreeterServiceSpec")

  val binding = Http()
    .newServerAt("localhost", 0)
    .bind(GreeterServiceHandler(new GreeterServiceImpl()))
    .futureValue

  val client = GreeterServiceClient(
    GrpcClientSettings.connectToServiceAt(
      "localhost",
      binding.localAddress.getPort
    ).withTls(false)
  )

  "A GreeterService" should {
    "respond to a unary request" in {
      val reply = client.sayHello(HelloRequest("Dave"))
      val r = scala.concurrent.Await.result(reply, 10.seconds)
      r.message shouldBe ("Hello, Dave!")
    }
  }

}