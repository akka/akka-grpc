/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import akka.annotation.ApiMayChange
import akka.grpc.ProtobufSerializer
import akka.util.ByteString
import com.google.protobuf.Parser

@ApiMayChange
class GoogleProtobufSerializer[T <: com.google.protobuf.Message](parser: Parser[T]) extends ProtobufSerializer[T] {

  // For binary compatibility in generated sources, can be dropped in version 2.x
  def this(clazz: Class[T]) =
    this(clazz.getMethod("parser").invoke(clazz).asInstanceOf[Parser[T]])

  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)
  override def deserialize(bytes: ByteString): T = {
    parser.parseFrom(bytes.toArray)
  }
}
