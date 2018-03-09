package com.lightbend.grpc.interop

abstract class GrpcClient {
  def run(args: Array[String]): Unit
}
