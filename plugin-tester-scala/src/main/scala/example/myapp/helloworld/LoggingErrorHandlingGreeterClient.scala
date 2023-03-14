/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import example.myapp.helloworld.grpc.{ GreeterServiceClient, HelloRequest }

import scala.concurrent.Await
import scala.concurrent.duration._

object LoggingErrorHandlingGreeterClient {
  def main(args: Array[String]): Unit = {
    // Boot akka
    implicit val sys = ActorSystem("LoggingErrorHandlingGreeterClient")

    // Take details how to connect to the service from the config.
    val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8082).withTls(false)
    // Create a client-side stub for the service
    val client: GreeterServiceClient = GreeterServiceClient(clientSettings)

    //#client-calls
    val successful = client.sayHello(HelloRequest("Martin"))
    Await.result(successful, 10.seconds)
    sys.log.info("Call succeeded")

    val failedBecauseLowercase = client.sayHello(HelloRequest("martin"))
    Await.result(failedBecauseLowercase.failed, 10.seconds)
    sys.log.info("Call with lowercase name failed")

    val failedBecauseEmpty = client.sayHello(HelloRequest(""))
    Await.result(failedBecauseEmpty.failed, 10.seconds)
    sys.log.info("Call with empty name failed")
    //#client-calls

    sys.terminate()
  }
}
