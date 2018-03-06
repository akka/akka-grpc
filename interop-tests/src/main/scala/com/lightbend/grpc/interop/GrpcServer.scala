package com.lightbend.grpc.interop

abstract class GrpcServer[T] {

  def label: String
  def pendingCases: Set[String]

  def start(): T
  def stop(binding: T): Unit
}
