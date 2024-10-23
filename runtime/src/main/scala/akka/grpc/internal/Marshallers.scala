/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.{ ByteArrayInputStream, InputStream }

import io.grpc.KnownLength
import akka.annotation.InternalStableApi
import akka.grpc.ProtobufSerializer

/**
 * INTERNAL API
 */
@InternalStableApi
abstract class BaseMarshaller[T](val protobufSerializer: ProtobufSerializer[T])
    extends io.grpc.MethodDescriptor.Marshaller[T]
    with WithProtobufSerializer[T] {
  override def parse(stream: InputStream): T =
    protobufSerializer.deserialize(stream)
}

/**
 * INTERNAL API
 */
@InternalStableApi
final class Marshaller[T <: scalapb.GeneratedMessage](protobufSerializer: ProtobufSerializer[T])
    extends BaseMarshaller[T](protobufSerializer) {
  override def parse(stream: InputStream): T = super.parse(stream)
  override def stream(value: T): InputStream =
    new ByteArrayInputStream(value.toByteArray) with KnownLength
}

/**
 * INTERNAL API
 */
@InternalStableApi
class ProtoMarshaller[T <: com.google.protobuf.Message](protobufSerializer: ProtobufSerializer[T])
    extends BaseMarshaller[T](protobufSerializer) {
  override def parse(stream: InputStream): T = super.parse(stream)
  override def stream(value: T): InputStream =
    new ByteArrayInputStream(value.toByteArray) with KnownLength
}
