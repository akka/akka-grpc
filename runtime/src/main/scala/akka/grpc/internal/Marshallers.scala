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
    val buffer =
      new Array[Byte](stream match {
        case k: KnownLength => math.max(0, k.available()) // No need to oversize this if we already know the size
        case _              => 32 * 1024
      })

    // Blocking calls underneath...
    // we can't avoid it for the moment because we are relying on the Netty's Channel API
    val bytes = stream.read(buffer, 0, buffer.length) match {
      case -1 =>
        akka.util.ByteString.empty // EOF immediately
      case n if n < buffer.length =>
        akka.util.ByteString
          .fromArrayUnsafe(buffer, 0, n) // Potentially wasteful, can we bet that the ByteString will be thrown away?
      case full =>
        val baos = new ByteArrayOutputStream(buffer.length * 2) // To avoid immediate resize
        var bytesRead = full

        do {
          baos.write(buffer, 0, bytesRead)
          bytesRead = stream.read(buffer)
        } while (bytesRead >= 0)

        akka.util.ByteString.fromArrayUnsafe(baos.toByteArray)
    }

    protobufSerializer.deserialize(bytes)
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
