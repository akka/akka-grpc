/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scalatestplus.play

import akka.grpc.internal.AkkaGrpcClientFactory
import akka.grpc.play.AkkaGrpcClientHelpers
import akka.grpc.scaladsl.AkkaGrpcClient
import org.scalatestplus.play.NewServerProvider
import play.api.test.RunningServer
import scala.reflect.ClassTag

/**
 * Helpers to test gRPC clients with Play using ScalaTest.
 *
 * Mixes a method into [[AkkaGrpcClientHelpers]] that knows how to configure
 */
trait ServerGrpcClient extends AkkaGrpcClientHelpers { this: NewServerProvider =>

  /** Configure the factory by combining the current app and server information */
  implicit def configuredAkkaGrpcClientFactory[T <: AkkaGrpcClient: ClassTag](implicit running: RunningServer): AkkaGrpcClientFactory.Configured[T] = {
    AkkaGrpcClientHelpers.factoryForAppEndpoints(running.app, running.endpoints)
  }

}
