/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.{ ByteArrayInputStream, InputStream }

import io.grpc.KnownLength
import akka.annotation.InternalStableApi
import akka.grpc.ProtobufSerializer
import scala.annotation.tailrec

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

    @tailrec def readBytes(buffer: Array[Byte], at: Int, result: akka.util.ByteString): akka.util.ByteString = {
      val capacity = buffer.length - at
      val bytesRead = stream.read(buffer, at, capacity)
      if (bytesRead < 0) {
        if (at > 0)
          result ++ akka.util.ByteString.fromArrayUnsafe(buffer, 0, at) // Potentially wasteful since at could be small
        else result
      } else {
        // Reading 0 bytes from an EOF stream should still EOF (-1) but didn't so has more data
        if (capacity == 0 && bytesRead == 0)
          readBytes(buffer, 0, result ++ akka.util.ByteString(buffer))
        else
          readBytes(buffer, at + bytesRead, result)
      }
    }

    protobufSerializer.deserialize(readBytes(buffer, 0, akka.util.ByteString.empty))
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
