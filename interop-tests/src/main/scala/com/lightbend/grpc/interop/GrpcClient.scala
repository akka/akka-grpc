package com.lightbend.grpc.interop

abstract class GrpcClient {
  def pendingCases: Set[String]
  def label: String
  def run(args: Array[String]): Unit
}
