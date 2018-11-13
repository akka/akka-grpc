/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scalatestplus.play

import akka.grpc.internal.AkkaGrpcClientFactory
import akka.grpc.play.AkkaGrpcClientHelpers
import akka.grpc.scaladsl.AkkaGrpcClient
import play.api.Application
import play.api.test.{ DefaultTestServerFactory, RunningServer }
import scala.reflect.ClassTag
import org.scalatest.TestData
import org.scalatestplus.play.BaseOneServerPerTest
import scala.reflect.runtime.{ universe => ru }

/**
 * Helpers to test gRPC clients with Play using ScalaTest.
 *
 * Mixes a method into [[AkkaGrpcClientHelpers]] that knows how to configure
 */
trait ServerGrpcClient extends AkkaGrpcClientHelpers { this: BaseOneServerPerTest =>

  /** Configure the factory by combining the current app and server information */
  implicit def configuredAkkaGrpcClientFactory[T <: AkkaGrpcClient: ru.TypeTag](implicit running: RunningServer): AkkaGrpcClientFactory.Configured[T] = {
    AkkaGrpcClientHelpers.factoryForAppEndpoints(running.app, running.endpoints)
  }

  override protected def newServerForTest(app: Application, testData: TestData): RunningServer =
    DefaultTestServerFactory.start(app)

}
