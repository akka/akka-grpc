/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.akka.grpc.client

import akka.actor.ActorSystem
import akka.discovery.{ ServiceDiscovery, SimpleServiceDiscovery }
import akka.grpc.GrpcClientSettings

import scala.concurrent.duration._

object GrpcClientSettingsCompileOnly {

  implicit val actorSystem = ActorSystem()
  //#simple
  GrpcClientSettings("localhost", 443)
  //#simple

  //#simple-programmatic
  GrpcClientSettings("localhost", 443)
    .withDeadline(1.second)
    .withTls(false)
  //#simple-programmatic

  //#provide-sd
  // An ActorSystem's default service discovery mechanism
  GrpcClientSettings(
    serviceName = "my-service",
    defaultPort = 443,
    serviceDiscoveryMechanism = "config")
  //#provide-sd
}
