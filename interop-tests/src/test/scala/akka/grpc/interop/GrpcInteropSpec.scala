/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

import io.grpc.testing.integration.TestServiceHandlerFactory

class GrpcInteropIoWithIoSpec extends GrpcInteropTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
class GrpcInteropIoWithAkkaScalaSpec extends GrpcInteropTests(IoGrpcJavaServerProvider, AkkaNettyClientProviderScala)
class GrpcInteropIoWithAkkaJavaSpec extends GrpcInteropTests(IoGrpcJavaServerProvider, AkkaNettyClientProviderJava)

class GrpcInteropAkkaScalaWithIoSpec extends GrpcInteropTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
class GrpcInteropAkkaScalaWithAkkaScalaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderScala, AkkaNettyClientProviderScala)
class GrpcInteropAkkaScalaWithAkkaJavaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderScala, AkkaNettyClientProviderJava)

// TODO testing against grpc-java server (problem with the path, still to be diagnosed)
class GrpcInteropAkkaHttpScalaServerWithAkkaHttpScalaClientSpec
    extends GrpcInteropTests(AkkaHttpServerProviderScala, AkkaHttpClientProviderScala)

class GrpcInteropAkkaJavaWithIoSpec extends GrpcInteropTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
class GrpcInteropAkkaJavaWithAkkaScalaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderJava, AkkaNettyClientProviderScala)
class GrpcInteropAkkaJavaWithAkkaJavaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderJava, AkkaNettyClientProviderJava)

object AkkaHttpServerProviderJava extends AkkaHttpServerProvider {
  val label: String = "akka-grpc java server"

  val pendingCases =
    Set("custom_metadata")

  val server = new AkkaGrpcServerJava((mat, sys) => {
    TestServiceHandlerFactory.create(new JavaTestServiceImpl(mat), sys)
  })
}

abstract class AkkaClientProviderScala(backend: String) extends AkkaClientProvider {
  val label: String = "akka-grpc scala client tester"

  def client = AkkaGrpcClientScala(settings => implicit sys => new AkkaGrpcScalaClientTester(settings, backend))
}

object AkkaNettyClientProviderScala extends AkkaClientProviderScala("netty") {
  val pendingCases =
    Set(
      "cancel_after_begin",
      "cancel_after_first_response",
      "timeout_on_sleeping_server",
      "custom_metadata",
      "client_compressed_unary",
      "client_compressed_streaming",
      "server_compressed_unary")
}

object AkkaHttpClientProviderScala extends AkkaClientProviderScala("akka-http") {
  val pendingCases =
    Set(
      "cancel_after_begin",
      "cancel_after_first_response",
      "timeout_on_sleeping_server",
      "custom_metadata",
      "client_compressed_unary",
      "client_compressed_streaming",
      "server_compressed_unary")
}

object AkkaNettyClientProviderJava extends AkkaClientProvider {
  val label: String = "akka-grpc java client tester"

  val pendingCases =
    Set(
      "cancel_after_begin",
      "cancel_after_first_response",
      "timeout_on_sleeping_server",
      "custom_metadata",
      "client_compressed_unary",
      "client_compressed_streaming",
      "server_compressed_unary")

  def client = new AkkaGrpcClientJava((settings, sys) => new AkkaGrpcJavaClientTester(settings, sys))
}
