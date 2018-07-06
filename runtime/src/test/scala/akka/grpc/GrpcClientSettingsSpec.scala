/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import akka.actor.ActorSystem
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

class GrpcClientSettingsSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  val sys = ActorSystem("test", ConfigFactory.parseString(
    """
        akka.grpc.client {
           "*" {
             # global defaults
             deadline = 66 m
             user-agent = "default-gRPC"
           }
          "project.WithSpecificConfiguration" {
            host = "myhost"
            port = 42
            override-authority = "specific-host.fr"
            trusted-ca-certificate = "specific-host.pem"
            deadline = 10m
            user-agent = "specific-host-gRPC"
          }
          "defaults-no-host" {
            # just a service name identifying defaults
            override-authority = "no-host.com"
            trusted-ca-certificate = "no-host.pem"
            deadline = 13m
            user-agent = "no-host-gRPC"
          }
          "fallback-to-defaults" {
            host = "fallback.to.defaults"
            port = 424242
          }
        }
      """).withFallback(ConfigFactory.load()))

  "The gRPC client settings" must {

    "fall back to the default configuration if no specific configuration is found" in {
      // Should not crash:
      GrpcClientSettings("project.WithoutSpecificConfiguration", sys)
    }

    "parse configuration values" in {
      val parsed = GrpcClientSettings("project.WithSpecificConfiguration", sys)
      parsed.host should ===(Some("myhost"))
      parsed.port should ===(Some(42))
      parsed.overrideAuthority should ===(Some("specific-host.fr"))
      parsed.trustedCaCertificate should ===(Some("specific-host.pem"))
      parsed.deadline should ===(10.minutes)
      parsed.userAgent should ===(Some("specific-host-gRPC"))
    }

    "use defaults from config" in {
      val settings = GrpcClientSettings(sys)
      settings.host should ===(None)
      settings.port should ===(None)
      settings.deadline should ===(66.minutes)
      settings.userAgent should ===(Some("default-gRPC"))
    }

    "use defaults from config for a specific host" in {
      val settings = GrpcClientSettings("defaults-no-host", sys)
      settings.host should ===(None)
      settings.port should ===(None)
      settings.deadline should ===(13.minutes)
      settings.overrideAuthority should be(Some("no-host.com"))
      settings.trustedCaCertificate should be(Some("no-host.pem"))
      settings.userAgent should ===(Some("no-host-gRPC"))
    }

    "fallback to defaults from config for a specific host" in {
      val settings = GrpcClientSettings("fallback-to-defaults", sys)
      settings.host should ===(Some("fallback.to.defaults"))
      settings.port should ===(Some(424242))
      settings.deadline should ===(66.minutes)
      settings.overrideAuthority should be(None)
      settings.trustedCaCertificate should be(None)
      settings.userAgent should ===(Some("default-gRPC"))
    }
  }

  override protected def afterAll(): Unit = {
    sys.terminate()
  }
}
