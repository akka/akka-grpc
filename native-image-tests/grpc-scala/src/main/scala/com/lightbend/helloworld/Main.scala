/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.helloworld

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

import scala.util.Failure
import scala.util.Success

object Main {

  def main(args: Array[String]): Unit = {

    // important to enable HTTP/2 in ActorSystem's config
    val conf =
      ConfigFactory.parseString("akka.http.server.enable-http2 = on").withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem[Nothing](Behaviors.empty[Nothing], "GrpcNativeTest", conf)

    new GreeterServer(system).run()

    import system.executionContext


    GreeterClient.runTests()(system).onComplete {
      case Failure(exception) =>
        println("Tests failed")
        exception.printStackTrace()
        System.exit(1)
      case Success(_) =>
        println("All tests succeeded")
        system.terminate()
    }
  }

}
