/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play.api.specs2

import akka.grpc.internal.AkkaGrpcClientFactory
import akka.grpc.play.AkkaGrpcClientHelpers
import akka.grpc.scaladsl.AkkaGrpcClient
import play.api.test.RunningServer

import scala.reflect.runtime.{ universe => ru }

/**
 * Helpers to test gRPC clients with Play using Specs2.
 *
 * Mixes a method into [[AkkaGrpcClientHelpers]] that knows how to configure
 * gRPC clients for the running server.
 */
trait ServerGrpcClient extends AkkaGrpcClientHelpers {

  /** Configure the factory by combining the app and the current implicit server information */
  implicit def configuredAkkaGrpcClientFactory[T <: AkkaGrpcClient: ru.TypeTag](implicit running: RunningServer): AkkaGrpcClientFactory.Configured[T] = {
    AkkaGrpcClientHelpers.factoryForAppEndpoints(running.app, running.endpoints)
  }

}
