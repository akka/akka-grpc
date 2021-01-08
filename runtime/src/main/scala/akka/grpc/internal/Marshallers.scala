/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream }

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
  override def parse(stream: InputStream): T = {
    val baos = new ByteArrayOutputStream(math.max(64, stream.available()))
    val buffer = new Array[Byte](32 * 1024)

    // Blocking calls underneath...
    // we can't avoid it for the moment because we are relying on the Netty's Channel API
    var bytesRead = stream.read(buffer)
    while (bytesRead >= 0) {
      baos.write(buffer, 0, bytesRead)
      bytesRead = stream.read(buffer)
    }
    protobufSerializer.deserialize(akka.util.ByteString(baos.toByteArray))
  }

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
