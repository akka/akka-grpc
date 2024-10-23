/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop.app

import akka.actor.ActorSystem
import akka.grpc.interop.AkkaHttpServerProviderScala
import akka.http.scaladsl.Http

/**
 * Scala application that starts a web server at localhost serving the test
 * application used for the gRPC integration tests.
 *
 * This can be useful for 'manually' interacting with this server.
 *
 * You can start this app from sbt with 'akka-grpc-interop-tests/test:reStart'
 */
object AkkaHttpServerAppScala extends App {
  val (sys: ActorSystem, binding: Http.ServerBinding) = AkkaHttpServerProviderScala.server.start(Array())
  sys.log.info(s"Bound to ${binding.localAddress}")
}
