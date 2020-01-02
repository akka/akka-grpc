/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

/**
 * Glue code to start a gRPC client (either akka-grpc or io.grpc) to test with
 */
abstract class GrpcClient {
  def run(args: Array[String]): Unit
}
