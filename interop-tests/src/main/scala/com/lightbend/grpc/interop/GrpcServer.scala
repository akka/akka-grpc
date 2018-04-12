/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.grpc.interop

abstract class GrpcServer[T] {
  @throws[Exception]
  def start(): T

  def getPort(binding: T): Int

  @throws[Exception]
  def stop(binding: T): Unit
}
