/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

import io.grpc.internal.testing.TestUtils
import io.grpc.testing.integration2.{ GrpcJavaClientTester, Settings, TestServiceClient }

object IoGrpcClient extends GrpcClient {
  override def run(args: Array[String]): Unit = {
    TestUtils.installConscryptIfAvailable()
    val settings = Settings.parseArgs(args)

    val client = new TestServiceClient(new GrpcJavaClientTester(settings))
    client.setUp()

    try client.run(settings)
    finally {
      client.tearDown()
    }
  }
}
