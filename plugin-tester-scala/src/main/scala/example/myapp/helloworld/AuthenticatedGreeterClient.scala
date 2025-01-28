/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import example.myapp.helloworld.grpc._

import scala.concurrent.Await
import scala.concurrent.duration._

object AuthenticatedGreeterClient {
  def main(args: Array[String]): Unit = {
    // Boot akka
    implicit val sys = ActorSystem("HelloWorldClient")

    // Take details how to connect to the service from the config.
    val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8082).withTls(false)
    // Create a client-side stub for the service
    val client: GreeterServiceClient = GreeterServiceClient(clientSettings)

    val failureWhenUnauthenticated = Await.result(client.sayHello(HelloRequest("Alice")).failed, 10.seconds)
    sys.log.warning(s"Call without authentication fails: $failureWhenUnauthenticated")

    val replyWhenAuthenticated =
      Await.result(client.sayHello().addHeader("Token", "XYZ").invoke(HelloRequest("Alice")), 10.seconds)
    sys.log.warning(s"Call with authentication succeeds: $replyWhenAuthenticated")

    val interceptedClient: GreeterServiceClient =
      client.addRequestHeader("Token", "XYZ")

    val replyWhenInterceptAuthenticated =
      Await.result(interceptedClient.sayHello(HelloRequest("Alice")), 10.seconds)
    sys.log.warning(s"Call with intercepted authentication succeeds: $replyWhenInterceptAuthenticated")

  }
}
