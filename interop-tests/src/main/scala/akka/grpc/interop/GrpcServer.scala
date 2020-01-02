/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

/**
 * Glue code to start a gRPC server (either akka-grpc or io.grpc) to test against
 */
abstract class GrpcServer[T] {
  @throws[Exception]
  def start(): T

  def getPort(binding: T): Int

  @throws[Exception]
  def stop(binding: T): Unit
}
