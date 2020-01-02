/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.Identity
import akka.grpc.internal.GrpcResponseHelpers
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status
import io.grpc.testing.integration.TestServiceHandlerFactory
import io.grpc.testing.integration.messages._
import io.grpc.testing.integration.test.{ TestService, TestServiceHandler, TestServiceMarshallers }
import org.scalatest._

import scala.collection.immutable
import scala.concurrent.Promise

class GrpcInteropIoWithIoSpec extends GrpcInteropTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
class GrpcInteropIoWithAkkaScalaSpec extends GrpcInteropTests(IoGrpcJavaServerProvider, AkkaHttpClientProviderScala)
class GrpcInteropIoWithAkkaJavaSpec extends GrpcInteropTests(IoGrpcJavaServerProvider, AkkaHttpClientProviderJava)

class GrpcInteropAkkaScalaWithIoSpec extends GrpcInteropTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
class GrpcInteropAkkaScalaWithAkkaScalaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderScala, AkkaHttpClientProviderScala)
class GrpcInteropAkkaScalaWithAkkaJavaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderScala, AkkaHttpClientProviderJava)

class GrpcInteropAkkaJavaWithIoSpec extends GrpcInteropTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
class GrpcInteropAkkaJavaWithAkkaScalaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderJava, AkkaHttpClientProviderScala)
class GrpcInteropAkkaJavaWithAkkaJavaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderJava, AkkaHttpClientProviderJava)

object AkkaHttpServerProviderJava extends AkkaHttpServerProvider {
  val label: String = "akka-grpc java server"

  val pendingCases =
    Set("custom_metadata")

  val server = new AkkaGrpcServerJava((mat, sys) => {
    TestServiceHandlerFactory.create(new JavaTestServiceImpl(mat), mat, sys)
  })
}

object AkkaHttpClientProviderScala extends AkkaHttpClientProvider {
  val label: String = "akka-grpc scala client tester"

  def client = AkkaGrpcClientScala(settings => implicit mat => implicit as => new AkkaGrpcScalaClientTester(settings))
}

object AkkaHttpClientProviderJava extends AkkaHttpClientProvider {
  val label: String = "akka-grpc java client tester"

  def client = new AkkaGrpcClientJava((settings, mat, as) => new AkkaGrpcJavaClientTester(settings, mat, as))
}
