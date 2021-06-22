/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import akka.annotation.ApiMayChange
import akka.grpc.ProtobufSerializer
import akka.util.ByteString
import com.google.protobuf.Parser

import java.nio.ByteBuffer

@ApiMayChange
class GoogleProtobufSerializer[T <: com.google.protobuf.Message](parser: Parser[T]) extends ProtobufSerializer[T] {

  @deprecated("Kept for binary compatibility, use the main constructor instead", since = "akka-grpc 1.1.2")
  def this(clazz: Class[T]) =
    this(clazz.getMethod("parser").invoke(clazz).asInstanceOf[Parser[T]])

  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)
  override def deserialize(bytes: ByteString): T =
    parser.parseFrom(bytes.toArray)

  override def deserialize(buffer: ByteBuffer): T = parser.parseFrom(buffer)
}
