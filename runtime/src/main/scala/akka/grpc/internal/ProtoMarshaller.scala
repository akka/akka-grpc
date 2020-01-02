/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream }
import io.grpc.KnownLength
import akka.annotation.InternalApi
import akka.grpc.ProtobufSerializer

/**
 * INTERNAL API
 */
@InternalApi
class ProtoMarshaller[T <: com.google.protobuf.Message](u: ProtobufSerializer[T])
    extends io.grpc.MethodDescriptor.Marshaller[T] {
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
    u.deserialize(akka.util.ByteString(baos.toByteArray))
  }

  override def stream(value: T): InputStream =
    new ByteArrayInputStream(value.toByteArray) with KnownLength
}
