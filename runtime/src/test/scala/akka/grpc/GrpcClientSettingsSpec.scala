/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.collection.{ immutable => im }
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.config.ConfigServiceDiscovery

import org.scalatest.concurrent.ScalaFutures
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GrpcClientSettingsSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  val clientWithServiceDiscovery = ConfigFactory.parseString("""
        //#config-service-discovery
        akka.grpc.client {
          "project.WithConfigServiceDiscovery" {
            service-discovery {
              mechanism = "config"
              service-name = "from-config"
              # optional for discovery
              protocol = "tcp"
              port-name = "http"
            }
            port = 43
          }
        }
        //#config-service-discovery

        akka.discovery.config.services = {
           from-config {
             endpoints = [
               {
                 host = "cat"
                 port = 1234
               }
             ]
           },
           from-config-no-port  {
              endpoints = [
                {
                  host = "dog"
                }
              ]
           }
         }
        """)

  val clientWithNoServiceName = ConfigFactory.parseString("""
        akka.grpc.client {
          "project.WithNoServiceName" {
            service-discovery {
              mechanism = "config"
            }
            port = 43
          }
        }
        """)

  implicit val sys = ActorSystem(
    "test",
    ConfigFactory
      .parseString("""
        //#client-config
        akka.grpc.client {
          "project.WithSpecificConfiguration" {
            service-discovery {
              service-name = "my-service"
            }
            host = "my-host"
            port = 42
            override-authority = "google.fr"
            deadline = 10m
            user-agent = "Akka-gRPC"
          }
        }
        //#client-config
      """)
      .withFallback(clientWithServiceDiscovery)
      .withFallback(clientWithNoServiceName)
      .withFallback(ConfigFactory.load()))

  "The gRPC client settings spec" should {
    def sysWithCert(certFileName: String) = {
      val clientConfig = ConfigFactory.parseString(s"""
        akka.grpc.client {
          "project.WithSpecificConfiguration" {
            service-discovery {
              service-name = "my-service"
            }
            host = "my-host"
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
      ActorSystem("test", clientConfig.withFallback(defaultConfig).withFallback(clientWithServiceDiscovery))
    }

    "use static service discovery for connectToServiceAt" in {
      val settings = GrpcClientSettings.connectToServiceAt("host.com", 8080)
      val resolved = settings.serviceDiscovery.lookup("any", 1.second).futureValue
      resolved.addresses should be(Seq(ResolvedTarget("host.com", Some(8080), None)))
    }

    "uses host for static service discovery" in {
      val parsed = GrpcClientSettings.fromConfig("project.WithSpecificConfiguration")
      parsed.serviceName should be("my-service")
      val Seq(discovered) = parsed.serviceDiscovery.lookup(parsed.serviceName, 1.second).futureValue.addresses
      discovered.host should be("my-host")
      discovered.port should be(Some(42))
      parsed.overrideAuthority should be(Some("google.fr"))
      parsed.sslContext shouldBe defined
      parsed.deadline should be(10.minutes)
      parsed.userAgent should be(Some("Akka-gRPC"))
      parsed.useTls should be(true)
    }

    "load a user defined service discovery mechanism" in {
      //#sd-settings
      // sys is an ActorSystem which is required for service discovery
      val settings = GrpcClientSettings.fromConfig(clientName = "project.WithConfigServiceDiscovery")
      //#sd-settings

      settings.serviceDiscovery shouldBe a[ConfigServiceDiscovery]

      val resolvedWithPort = settings.serviceDiscovery.lookup("from-config", 1.second).futureValue
      resolvedWithPort should be(Resolved("from-config", im.Seq(ResolvedTarget("cat", Some(1234), None))))

      val resolvedWithNoPort = settings.serviceDiscovery.lookup("from-config-no-port", 1.second).futureValue
      resolvedWithNoPort should be(Resolved("from-config-no-port", im.Seq(ResolvedTarget("dog", None, None))))

      settings.servicePortName should be(Some("http"))
      settings.serviceProtocol should be(Some("tcp"))
    }

    "fail to parse configuration with non-existent certificate" in {
      val system = sysWithCert("no-such-cert.pem")
      try {
        val thrown = the[IllegalArgumentException] thrownBy
          GrpcClientSettings.fromConfig("project.WithSpecificConfiguration")(system)
        // We want a good message since missing classpath resources are difficult to debug
        thrown.getMessage should include("certs/no-such-cert.pem")
      } finally system.terminate()
    }

    "provide a useful error message if configuration missing" in {
      intercept[IllegalArgumentException] {
        GrpcClientSettings.fromConfig("project.MissingConfiguration")
      }.getMessage should be(
        "requirement failed: Config path `akka.grpc.client.project.MissingConfiguration` does not exist")
    }

    "fail fast if no service name" in {
      intercept[IllegalArgumentException] {
        GrpcClientSettings.fromConfig("project.WithNoServiceName")
      }.getMessage should be("requirement failed: Configuration must contain a service-name")
    }
    "fail fast when no service discovery is configured on the actor system" in {
      intercept[IllegalArgumentException] {
        val settings = GrpcClientSettings.usingServiceDiscovery("a-downstream-service")
      }
    }

    "use the service discovery configured on the actor system" in {
      // given an ActorSystem with a ServiceDiscovery method configured
      val sdConfig =
        s"""
          |akka.discovery {
          |  method = "fake-discovery"
          |  fake-discovery.class = "${classOf[FakeServiceDiscovery].getName}"
          |}
        """.stripMargin
      val actorSystem = ActorSystem("test-with-service-discovery", ConfigFactory.parseString(sdConfig))

      try {
        // invoking usingServiceDiscovery provides a GrpcClientSettings instance
        // that uses the ServiceDiscovery in the ActorSystem
        val settings = GrpcClientSettings.usingServiceDiscovery("a-downstream-service")(actorSystem)
        settings.serviceDiscovery should not be null
      } finally actorSystem.terminate()
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    sys.terminate()
  }
}
class FakeServiceDiscovery extends ServiceDiscovery {
  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = ???
}
