package com.lightbend.grpc.interop

import io.grpc.testing.integration.Util
import io.grpc.testing.integration2.{ GrpcJavaClientTester, Settings, TestServiceClient }

object IoGrpcClient extends GrpcClient {

  override def run(args: Array[String]): Unit = {
    Util.installConscryptIfAvailable()
    val settings = Settings.parseArgs(args)

    val client = new TestServiceClient(new GrpcJavaClientTester(settings))
    client.setUp()

    try
      client.run(settings)
    finally {
      client.tearDown()
    }
  }

}
