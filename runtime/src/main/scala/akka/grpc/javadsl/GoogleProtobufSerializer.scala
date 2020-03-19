/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.io.InputStream

import akka.annotation.ApiMayChange
import akka.grpc.{ ProtobufSerialization, ProtobufSerializer }
import akka.grpc.ProtobufSerialization.Protobuf
import akka.util.ByteString

@ApiMayChange
class GoogleProtobufSerializer[T <: com.google.protobuf.Message](defaultInstance: T) extends ProtobufSerializer[T] {
  override val format: ProtobufSerialization = Protobuf
  override def serialize(t: T): ByteString = ByteString.fromArrayUnsafe(t.toByteArray)
  override def deserialize(bytes: InputStream): T = defaultInstance.getParserForType.parseFrom(bytes).asInstanceOf[T]
}
