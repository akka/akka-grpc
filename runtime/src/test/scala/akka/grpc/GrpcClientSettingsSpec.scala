/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.grpc.internal.HardcodedServiceDiscovery
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

class GrpcClientSettingsSpec extends WordSpec with Matchers with ScalaFutures {
  "The gRPC client settings spec" should {
    val sys = ActorSystem("test", ConfigFactory.parseString(
      """
        |akka.grpc.client {
        |  "project.WithSpecificConfiguration" {
        |    host = "myhost"
        |    port = 42
        |    override-authority = "google.fr"
        |    trusted-ca-certificate = "ca.pem"
        |    deadline = 10m
        |    user-agent = "Akka-gRPC"
        |  }
        |}
      """.stripMargin).withFallback(ConfigFactory.load()))

    "fall back to the default configuration if no specific configuration is found" in {
      // Should not crash:
      GrpcClientSettings("project.WithoutSpecificConfiguration", sys)
    }

    "parse configuration values" in {
      val parsed = GrpcClientSettings("project.WithSpecificConfiguration", sys)
      parsed.name should be("myhost")
      val Seq(discovered) = parsed.serviceDiscovery.lookup(parsed.name, 1.second).futureValue.addresses
      discovered.host should be("myhost")
      discovered.port should be(Some(42))
      parsed.overrideAuthority should be(Some("google.fr"))
      parsed.trustedCaCertificate should be(Some("ca.pem"))
      parsed.deadline should be(10.minutes)
      parsed.userAgent should be(Some("Akka-gRPC"))
    }
  }
}
