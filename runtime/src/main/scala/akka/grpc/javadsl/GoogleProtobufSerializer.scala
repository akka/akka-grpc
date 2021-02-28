/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import akka.annotation.ApiMayChange
import akka.grpc.ProtobufSerializer
import akka.util.ByteString

@ApiMayChange
class GoogleProtobufSerializer[T <: com.google.protobuf.Message](clazz: Class[T]) extends ProtobufSerializer[T] {
  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)
  override def deserialize(bytes: ByteString): T = {
    val parser = clazz.getMethod("parseFrom", classOf[Array[Byte]])
    parser.invoke(clazz, bytes.toArray).asInstanceOf[T]
  }
}
