/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.grpc.interop

abstract class GrpcClient {
  def run(args: Array[String]): Unit
}
