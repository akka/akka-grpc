/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.pattern.AskTimeoutException

import akka.grpc.GrpcClientSettings

import org.scalatest._
import org.scalatest.concurrent._

class NettyClientUtilsSpec extends WordSpec with Matchers with ScalaFutures {
  "The Netty client-utilities" should {
    implicit val system = ActorSystem(
      "test",
      ConfigFactory.parseString("""
        akka.discovery.method = alwaystimingout

        akka.discovery.alwaystimingout.class = akka.grpc.internal.AlwaysTimingOutDiscovery
        """).withFallback(ConfigFactory.load()))

    implicit val ec = system.dispatcher

    "fail to create a channel when service discovery times out" in {
      val settings = GrpcClientSettings.usingServiceDiscovery("testService")

      val channel = NettyClientUtils.createChannel(settings)
      channel.done.failed.futureValue shouldBe a[AskTimeoutException]
    }
  }
}
