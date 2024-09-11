/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

import akka.actor.ActorSystem
import io.grpc.internal.testing.TestUtils
import io.grpc.testing.integration2.ClientTester
import io.grpc.testing.integration2.Settings
import io.grpc.testing.integration2.TestServiceClient

import scala.concurrent.Await
import scala.concurrent.duration._

// TODO #151 use our own Settings object
final case class AkkaGrpcClientScala(clientTesterFactory: Settings => ActorSystem => ClientTester) extends GrpcClient {
  override def run(args: Array[String]): Unit = {
    TestUtils.installConscryptIfAvailable()
    val settings = Settings.parseArgs(args)

    implicit val sys = ActorSystem()

    val client = new TestServiceClient(clientTesterFactory(settings)(sys))
    client.setUp()

    try client.run(settings)
    finally {
      client.tearDown()
      Await.result(sys.terminate(), 10.seconds)
    }
  }
}
