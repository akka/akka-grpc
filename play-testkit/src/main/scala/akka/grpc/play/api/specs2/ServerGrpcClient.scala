/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play.api.specs2

import scala.reflect.ClassTag
import akka.grpc.internal.{ AkkaGrpcClient, AkkaGrpcClientFactory }
import akka.grpc.play.AkkaGrpcClientHelpers
import play.api.test.{ NewForServer, RunningServer }

/**
 * Helpers to test gRPC clients with Play using Specs2.
 *
 * Mixes a method into [[AkkaGrpcClientHelpers]] that knows how to configure
 * gRPC clients for the running server.
 */
trait ServerGrpcClient extends AkkaGrpcClientHelpers { this: NewForServer =>

  /** Configure the factory by combining the app and the current implicit server information */
  implicit def configuredAkkaGrpcClientFactory[T <: AkkaGrpcClient: ClassTag](implicit running: RunningServer): AkkaGrpcClientFactory.Configured[T] = {
    AkkaGrpcClientHelpers.factoryForAppEndpoints(running.app, running.endpoints)
  }

}