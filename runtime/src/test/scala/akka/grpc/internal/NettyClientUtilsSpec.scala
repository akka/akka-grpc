/*
 * Copyright (C) 2019-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NettyClientUtilsSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem(
    "test",
    ConfigFactory
      .parseString("""
      akka.discovery.method = alwaystimingout

      akka.discovery.alwaystimingout.class = akka.grpc.internal.AlwaysTimingOutDiscovery
      """)
      .withFallback(ConfigFactory.load()))

  "The Netty client-utilities" should {
//    The channel can now retry service discovery as needed itself,
//    I guess we should test that instead?
//    "fail to create a channel when service discovery times out" in {
//      val settings = GrpcClientSettings.usingServiceDiscovery("testService")
//
//      val channel = NettyClientUtils.createChannel(settings)
//    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
