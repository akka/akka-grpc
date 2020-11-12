package akka.grpc.interop

/**
 */
trait GrpcClientProvider {
  def label: String
  def pendingCases: Set[String]

  def client: GrpcClient
}

object IoGrpcJavaClientProvider extends GrpcClientProvider {
  val label: String = "grpc-java client tester"

  val pendingCases =
    Set()

  val client = IoGrpcClient
}

trait AkkaClientProvider extends GrpcClientProvider

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
