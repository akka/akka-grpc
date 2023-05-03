/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import scala.concurrent.Await
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import com.google.protobuf.Timestamp
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span
import example.myapp.helloworld.grpc._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext

class JGreeterServiceSpec extends Matchers with AnyWordSpecLike with BeforeAndAfterAll with ScalaFutures {
  implicit val patience: PatienceConfig =
    PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  implicit val serverSystem: ActorSystem = {
    // important to enable HTTP/2 in server ActorSystem's config
    val conf =
      ConfigFactory.parseString("akka.http.server.enable-http2 = on").withFallback(ConfigFactory.defaultApplication())
    val sys = ActorSystem("GreeterServer", conf)
    // make sure servers are bound before using client
    GreeterServer.run(sys).toCompletableFuture.get
    PowerGreeterServer.run(sys).toCompletableFuture.get
    sys
  }

  val clientSystem = ActorSystem("GreeterClient")

  implicit val ec: ExecutionContext = clientSystem.dispatcher

  val clients = Seq(8090, 8091).map { port =>
    GreeterServiceClient.create(GrpcClientSettings.connectToServiceAt("127.0.0.1", port).withTls(false), clientSystem)
  }

  override def afterAll(): Unit = {
    Await.ready(clientSystem.terminate(), 5.seconds)
    Await.ready(serverSystem.terminate(), 5.seconds)
  }

  "GreeterService" should {
    "reply to single request" in {
      val reply = clients.head.sayHello(HelloRequest.newBuilder.setName("Alice").build())
      val timestamp = Timestamp.newBuilder.setSeconds(1234567890).setNanos(12345).build()
      val expectedResponse =
        HelloReply.newBuilder.setMessage("Hello, Alice").setTimestamp(timestamp).build()
      reply.toCompletableFuture.get should ===(expectedResponse)
    }
  }

  "GreeterServicePowerApi" should {
    Seq(
      ("Authorization", "Hello, Alice (authenticated)"),
      ("WrongHeaderName", "Hello, Alice (not authenticated)")).zipWithIndex.foreach {
      case ((mdName, expResp), ix) =>
        s"use metadata in replying to single request ($ix)" in {
          val reply = clients.last
            .sayHello()
            .addHeader(mdName, "Bearer test")
            .invoke(HelloRequest.newBuilder.setName("Alice").build())
          reply.toCompletableFuture.get should ===(HelloReply.newBuilder.setMessage(expResp).build())
        }
    }
  }
}
