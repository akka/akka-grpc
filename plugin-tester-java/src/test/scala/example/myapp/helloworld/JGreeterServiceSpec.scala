/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import scala.concurrent.Await

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.SSLContextUtils
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span

import example.myapp.helloworld.grpc._

class JGreeterServiceSpec
  extends Matchers
  with WordSpecLike
  with BeforeAndAfterAll
  with ScalaFutures {

  implicit val patience = PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  implicit val serverSystem: ActorSystem = {
    // important to enable HTTP/2 in server ActorSystem's config
    val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val sys = ActorSystem("GreeterServer", conf)
    val bound = GreeterServer.run(sys)
    // make sure server is bound before using client
    bound.toCompletableFuture.get
    sys
  }

  val clientSystem = ActorSystem("GreeterClient")

  implicit val mat = ActorMaterializer.create(clientSystem)
  implicit val ec = clientSystem.dispatcher

  val clients = Seq(8080, 8081).map { port =>
    GreeterServiceClient.create(
      GrpcClientSettings.connectToServiceAt("127.0.0.1", port)
        .withTls(false),
      mat, ec)
  }

  override def afterAll: Unit = {
    Await.ready(clientSystem.terminate(), 5.seconds)
    Await.ready(serverSystem.terminate(), 5.seconds)
  }

  "GreeterService" should {
    "reply to single request" in {
      val reply = clients.head.sayHello(HelloRequest.newBuilder.setName("Alice").build())
      reply.toCompletableFuture.get should ===(HelloReply.newBuilder.setMessage("Hello, Alice").build())
    }
  }

  "GreeterServicePowerApi" should {
    Seq(
      ("Authorization", "Hello, Alice (authenticated)"),
      ("WrongHeaderName", "Hello, Alice (not authenticated)")
    ).zipWithIndex.foreach {
        case ((mdName, expResp), ix) =>
          s"use metadata in replying to single request ($ix)" in {
            val reply = clients.last.sayHello()
              .addHeader(mdName, "<some auth token>")
              .invoke(HelloRequest.newBuilder.setName("Alice").build())
            reply.toCompletableFuture.get should ===(HelloReply.newBuilder.setMessage(expResp).build())
          }
      }
  }

}
