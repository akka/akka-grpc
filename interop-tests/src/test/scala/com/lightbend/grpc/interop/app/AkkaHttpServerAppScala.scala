/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.grpc.interop.app

import com.lightbend.grpc.interop.AkkaHttpServerProviderScala

object AkkaHttpServerAppScala extends App {
  AkkaHttpServerProviderScala.server.start()
}
