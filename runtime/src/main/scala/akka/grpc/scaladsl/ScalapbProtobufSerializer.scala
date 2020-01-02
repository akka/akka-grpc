/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.grpc.ProtobufSerializer
import akka.util.ByteString
import scalapb.{ GeneratedMessage, GeneratedMessageCompanion, Message }

class ScalapbProtobufSerializer[T <: GeneratedMessage with Message[T]](companion: GeneratedMessageCompanion[T])
    extends ProtobufSerializer[T] {
  override def serialize(t: T) = ByteString(companion.toByteArray(t))
  override def deserialize(bytes: ByteString): T = companion.parseFrom(bytes.iterator.asInputStream)
}
