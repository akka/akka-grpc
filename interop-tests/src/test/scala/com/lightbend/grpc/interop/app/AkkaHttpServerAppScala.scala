/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.grpc.interop.app

import com.lightbend.grpc.interop.AkkaHttpServerProviderScala

object AkkaHttpServerAppScala extends App {
  AkkaHttpServerProviderScala.server.start()
}
