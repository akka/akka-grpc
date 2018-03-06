package com.lightbend.grpc.interop

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import io.grpc.testing.integration.Util
import io.grpc.testing.integration2.{ ClientTester, Settings, TestServiceClient }
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }

case class AkkaGrpcClientScala(clientTesterFactory: Settings => Materializer => ExecutionContext => ClientTester) extends GrpcClient {

  val label: String = "akka-grpc client tester"


  val pendingCases =
    Set(
      "ping_pong",
      "empty_stream",
      "client_streaming",
      "cancel_after_begin",
      "cancel_after_first_response",
      "timeout_on_sleeping_server",
      "custom_metadata",
      "status_code_and_message",
      "client_compressed_unary",
      "client_compressed_streaming",
      "server_compressed_unary",
      "unimplemented_service",
    )

  override def run(args: Array[String]): Unit = {
    Util.installConscryptIfAvailable()
    val settings = Settings.parseArgs(args)

    implicit val sys = ActorSystem()
    implicit val mat = ActorMaterializer()

    val client = new TestServiceClient(clientTesterFactory(settings)(mat)(sys.dispatcher))
    client.setUp()

    try
      client.run(settings)
    finally {
      client.tearDown()
      Await.result(sys.terminate(), 10.seconds)
    }
  }

}
