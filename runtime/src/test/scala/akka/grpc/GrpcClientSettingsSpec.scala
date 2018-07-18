/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.io.IOException

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.duration._

class GrpcClientSettingsSpec extends WordSpec with Matchers with ScalaFutures {
  "The gRPC client settings spec" should {
    val sys = ActorSystem("test", ConfigFactory.parseString(
      """
        |akka.grpc.client {
        |  "project.WithSpecificConfiguration" {
        |    host = "myhost"
        |    port = 42
        |    override-authority = "google.fr"
        |    deadline = 10m
        |    user-agent = "Akka-gRPC"
        |  }
        |}
      """.stripMargin).withFallback(ConfigFactory.load()))

    def sysWithCert(certFileName: String) = {
      val clientConfig = ConfigFactory.parseString(
        s"""
        akka.grpc.client {
          "project.WithSpecificConfiguration" {
            host = "myhost"
            port = 42
            override-authority = "google.fr"
            ssl-config {
              disabledKeyAlgorithms = [] // Allow weak certificates
              trustManager {
                stores = [
                  {path = certs/$certFileName, classpath = true, type = PEM}
                ]
              }
            }
            deadline = 10m
            user-agent = "Akka-gRPC"
          }
        }
      """)
      val defaultConfig = ConfigFactory.load()
      ActorSystem("test", clientConfig.withFallback(defaultConfig))
    }

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
      parsed.sslContext shouldBe defined
      parsed.deadline should be(10.minutes)
      parsed.userAgent should be(Some("Akka-gRPC"))
    }

    "fail to parse configuration with non-existent certificate" in {
      val thrown = the[IllegalArgumentException] thrownBy
        GrpcClientSettings("project.WithSpecificConfiguration", sysWithCert("no-such-cert.pem"))
      // We want a good message since missing classpath resources are difficult to debug
      thrown.getMessage should include("certs/no-such-cert.pem")
    }
  }
}
