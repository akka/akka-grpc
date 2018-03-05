package com.lightbend.grpc.interop

abstract class GrpcServer[T] {
  def start(): T
  def stop(binding: T): Unit
}
