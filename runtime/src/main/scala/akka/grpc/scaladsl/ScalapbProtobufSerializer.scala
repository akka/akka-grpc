/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.annotation.ApiMayChange
import akka.grpc.ProtobufSerializer
import akka.util.ByteString
import com.google.protobuf.CodedInputStream
import scalapb.{ GeneratedMessage, GeneratedMessageCompanion }

import java.io.InputStream

@ApiMayChange
class ScalapbProtobufSerializer[T <: GeneratedMessage](companion: GeneratedMessageCompanion[T])
    extends ProtobufSerializer[T] {
  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)
  override def deserialize(bytes: ByteString): T =
    companion.parseFrom(CodedInputStream.newInstance(bytes.asByteBuffer))
  override def deserialize(data: InputStream): T =
    companion.parseFrom(data)
}
