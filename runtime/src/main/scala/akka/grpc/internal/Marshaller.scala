/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.{ FilterInputStream, InputStream }

import akka.annotation.InternalApi
import akka.grpc.ProtobufSerializer
import io.grpc.KnownLength

/**
 * INTERNAL API
 */
@InternalApi
final class Marshaller[T](u: ProtobufSerializer[T]) extends io.grpc.MethodDescriptor.Marshaller[T] {
  override def parse(stream: InputStream): T = u.deserialize(stream)

  override def stream(value: T): InputStream with KnownLength = {
    val serialized = u.serialize(value)
    new FilterInputStream(serialized.iterator.asInputStream) with KnownLength {
      override def available(): Int = serialized.length
    }
  }
}
