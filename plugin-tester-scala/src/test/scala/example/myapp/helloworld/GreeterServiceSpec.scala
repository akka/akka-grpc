/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import akka.actor.{ ActorSystem, ClassicActorSystemProvider }
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc._
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.Await
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class GreeterSpec extends Matchers with AnyWordSpecLike with BeforeAndAfterAll with ScalaFutures {

  implicit val patience = PatienceConfig(10.seconds, Span(100, org.scalatest.time.Millis))

  implicit val serverSystem: ActorSystem = {
    // important to enable HTTP/2 in server ActorSystem's config
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val sys = ActorSystem("GreeterServer", conf)
    // make sure servers are bound before using client
    new GreeterServer(sys).run().futureValue
    new PowerGreeterServer(sys).run().futureValue
    sys
  }

  val clientSystem = ActorSystem("GreeterClient")

  implicit val mat = ActorMaterializer.create(clientSystem)
  implicit val ec = clientSystem.dispatcher

  val clients = Seq(8080, 8081).map { port =>
    GreeterServiceClient(
      GrpcClientSettings
        .connectToServiceAt("127.0.0.1", port)(clientSystem.asInstanceOf[ClassicActorSystemProvider])
        .withTls(false))(clientSystem.asInstanceOf[ClassicActorSystemProvider])
  }

  override def afterAll: Unit = {
    Await.ready(clientSystem.terminate(), 5.seconds)
    Await.ready(serverSystem.terminate(), 5.seconds)
  }

  "GreeterService" should {
    "reply to single request" in {
      val reply = clients.head.sayHello(HelloRequest("Alice"))
      reply.futureValue should ===(HelloReply("Hello, Alice"))
    }
  }

  "GreeterServicePowerApi" should {
    Seq(
      ("Authorization", "Hello, Alice (authenticated)"),
      ("WrongHeaderName", "Hello, Alice (not authenticated)")).zipWithIndex.foreach {
      case ((mdName, expResp), ix) =>
        s"use metadata in replying to single request ($ix)" in {
          val reply = clients.last.sayHello().addHeader(mdName, "<some auth token>").invoke(HelloRequest("Alice"))
          reply.futureValue should ===(HelloReply(expResp))
        }
    }
  }
}
