package docs.akka.grpc.client

import akka.actor.ActorSystem
import akka.discovery.{ ServiceDiscovery, SimpleServiceDiscovery }
import akka.grpc.GrpcClientSettings

import scala.concurrent.duration._

object GrpcClientSettingsCompileOnly {

  //#simple
  GrpcClientSettings("localhost", 443)
  //#simple

  //#simple-programmatic
  GrpcClientSettings("localhost", 443)
    .withDeadline(1.second)
    .withTls(false)
  //#simple-programmatic

  val actorSystem = ActorSystem()
  //#provide-sd
  // An ActorSystem's default service discovery mechanism
  val serviceDiscovery: SimpleServiceDiscovery = ServiceDiscovery(actorSystem).discovery

  GrpcClientSettings(
    serviceName = "my-service",
    defaultPort = 443,
    serviceDiscovery = serviceDiscovery,
    10.seconds)
  //#provide-sd
}
