/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.io.IOException

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.config.ConfigSimpleServiceDiscovery
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.duration._
import scala.collection.{ immutable => im }

class GrpcClientSettingsSpec extends WordSpec with Matchers with ScalaFutures {
  "The gRPC client settings spec" should {
    val clientWithServiceDiscovery = ConfigFactory.parseString(
      """
        //#config-service-discovery
        akka.grpc.client {
          "project.WithConfigServiceDiscovery" {
            service-discovery-mechanism = "config"
            service-name = "from-config"
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

    val sys = ActorSystem("test", ConfigFactory.parseString(
      """
        //#client-config
        akka.grpc.client {
          "project.WithSpecificConfiguration" {
            service-name = "myhost"
            port = 42
            override-authority = "google.fr"
            deadline = 10m
            user-agent = "Akka-gRPC"
          }
        }
        //#client-config
      """)
      .withFallback(clientWithServiceDiscovery)
      .withFallback(ConfigFactory.load()))

    def sysWithCert(certFileName: String) = {
      val clientConfig = ConfigFactory.parseString(
        s"""
        akka.grpc.client {
          "project.WithSpecificConfiguration" {
            service-name = "myhost"
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

    "provide a useful error message if configuration missing" in {
      intercept[IllegalArgumentException] {
        GrpcClientSettings("project.MissingConfiguration", sys)
      }.getMessage should be("requirement failed: Config path `akka.grpc.client.project.MissingConfiguration` does not exist")
    }

    "parse configuration values" in {
      val parsed = GrpcClientSettings("project.WithSpecificConfiguration", sys)
      parsed.serviceName should be("myhost")
      val Seq(discovered) = parsed.serviceDiscovery.lookup(parsed.serviceName, 1.second).futureValue.addresses
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

    "load a user defined service discovery mechanism" in {
      //#sd-settings
      // sys is an ActorSystem which is required for service discovery
      val settings = GrpcClientSettings(
        clientName = "project.WithConfigServiceDiscovery",
        actorSystem = sys)
      //#sd-settings

      settings.serviceDiscovery shouldBe a[ConfigSimpleServiceDiscovery]

      val resolvedWithPort = settings.serviceDiscovery.lookup("from-config", 1.second).futureValue
      resolvedWithPort should be(Resolved("from-config", im.Seq(ResolvedTarget("cat", Some(1234)))))

      val resolvedWithNoPort = settings.serviceDiscovery.lookup("from-config-no-port", 1.second).futureValue
      resolvedWithNoPort should be(Resolved("from-config-no-port", im.Seq(ResolvedTarget("dog", None))))
    }
  }
}
