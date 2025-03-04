/*
 * Copyright (C) 2020-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.grpc.ProtobufSerializer

trait WithProtobufSerializer[T] {
  def protobufSerializer: ProtobufSerializer[T]
}
