/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.grpc.interop

import io.grpc.testing.integration2.TestServiceServer

object IoGrpcServer extends GrpcServer[TestServiceServer] {

  @volatile var didAlreadyWarn = false

  override def start() = {
    val server = new TestServiceServer
    if (server.useTls && !didAlreadyWarn) {
      didAlreadyWarn = true
      println("\nUsing fake CA for TLS certificate. Test clients should expect host\n" +
        "*.test.google.fr and our test CA. For the Java test client binary, use:\n" +
        "--server_host_override=foo.test.google.fr --use_test_ca=true\n")
    }

    server.start()
    server
  }

  override def stop(binding: TestServiceServer) = binding.stop()

  override def getPort(binding: TestServiceServer): Int = binding.port
}
