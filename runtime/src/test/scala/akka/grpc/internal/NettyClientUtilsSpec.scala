/*
 * Copyright (C) 2019-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.pattern.AskTimeoutException

import akka.grpc.GrpcClientSettings

import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NettyClientUtilsSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val system = ActorSystem(
    "test",
    ConfigFactory.parseString("""
      akka.discovery.method = alwaystimingout

      akka.discovery.alwaystimingout.class = akka.grpc.internal.AlwaysTimingOutDiscovery
      """).withFallback(ConfigFactory.load()))

  "The Netty client-utilities" should {
    implicit val ec = system.dispatcher

    "fail to create a channel when service discovery times out" in {
      val settings = GrpcClientSettings.usingServiceDiscovery("testService")

      val channel = NettyClientUtils.createChannel(settings)
      channel.failed.futureValue shouldBe a[AskTimeoutException]
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
