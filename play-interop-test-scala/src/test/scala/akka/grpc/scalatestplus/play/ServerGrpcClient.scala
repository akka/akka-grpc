/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scalatestplus.play

import akka.grpc.internal.{AkkaGrpcClient, AkkaGrpcClientFactory}
import akka.grpc.play.AkkaGrpcClientHelpers
import org.scalatestplus.play.NewServerProvider
import play.api.test.RunningServer

/**
 * Helpers to test gRPC clients with Play using ScalaTest.
 *
 * Mixes a method into [[AkkaGrpcClientHelpers]] that knows how to configure
 */
trait ServerGrpcClient extends AkkaGrpcClientHelpers { this: NewServerProvider =>

  /** Configure the factory by combining the current app and server information */
  implicit def configuredAkkaGrpcClientFactory[T <: AkkaGrpcClient: AkkaGrpcClientFactory](implicit server: RunningServer): AkkaGrpcClientFactory.Configured[T] = {
    AkkaGrpcClientHelpers.factoryForAppEndpoints(app, server.endpoints)
  }

}