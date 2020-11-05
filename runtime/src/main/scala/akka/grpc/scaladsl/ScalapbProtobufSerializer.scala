/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.annotation.ApiMayChange
import akka.grpc.ProtobufSerializer
import akka.util.ByteString
import com.google.protobuf.CodedInputStream
import scalapb.{ GeneratedMessage, GeneratedMessageCompanion }

@ApiMayChange
class ScalapbProtobufSerializer[T <: GeneratedMessage](companion: GeneratedMessageCompanion[T])
    extends ProtobufSerializer[T] {
  override def serialize(t: T): ByteString = ByteString(companion.toByteArray(t))
  override def deserialize(bytes: ByteString): T =
    companion.parseFrom(CodedInputStream.newInstance(bytes.asByteBuffer))
}
