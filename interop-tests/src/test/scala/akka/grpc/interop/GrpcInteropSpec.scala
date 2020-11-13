/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

import io.grpc.testing.integration.TestServiceHandlerFactory

class GrpcInteropIoWithIoSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.IoGrpc)
class GrpcInteropIoWithAkkaNettyScalaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaNetty.Scala)
class GrpcInteropIoWithAkkaNettyJavaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaNetty.Java)
class GrpcInteropIoWithAkkaHttpScalaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaHttp.Scala)
//class GrpcInteropIoWithAkkaHttpJavaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaHttp.Java)

class GrpcInteropAkkaScalaWithIoSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.IoGrpc)
class GrpcInteropAkkaScalaWithAkkaNettyScalaSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.AkkaNetty.Scala)
class GrpcInteropAkkaScalaWithAkkaNettyJavaSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.AkkaNetty.Java)
class GrpcInteropAkkaScalaWithAkkaHttpScalaSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.AkkaHttp.Scala)
//class GrpcInteropAkkaScalaWithAkkaHttpJavaSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.AkkaHttp.Java)

class GrpcInteropAkkaJavaWithIoSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.IoGrpc)
class GrpcInteropAkkaJavaWithAkkaNettyScalaSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.AkkaNetty.Scala)
class GrpcInteropAkkaJavaWithAkkaNettyJavaSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.AkkaNetty.Java)
class GrpcInteropAkkaJavaWithAkkaHttpScalaSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.AkkaHttp.Scala)
//class GrpcInteropAkkaJavaWithAkkaHttpJavaSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.AkkaHttp.Java)

//--- Aliases

object Servers {
  val IoGrpc = IoGrpcJavaServerProvider
  object Akka {
    val Java = AkkaHttpServerProviderJava
    val Scala = AkkaHttpServerProviderScala
  }
}

object Clients {
  val IoGrpc = IoGrpcJavaClientProvider
  object AkkaNetty {
    val Java = AkkaNettyClientProviderJava
    val Scala = AkkaNettyClientProviderScala
  }
  object AkkaHttp {
    // FIXME: let's have Scala stable and we'll do Java later.
    // val Java = AkkaHttpClientProviderJava
    val Scala = AkkaHttpClientProviderScala
  }
}

//--- Some more providers

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
