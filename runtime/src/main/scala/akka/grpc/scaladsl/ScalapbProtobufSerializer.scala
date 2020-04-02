/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.io.InputStream

import akka.annotation.ApiMayChange
import akka.grpc.{ ProtobufSerialization, ProtobufSerializer }
import akka.grpc.ProtobufSerialization.Protobuf
import akka.util.ByteString
import com.github.ghik.silencer.silent
import scalapb.{ GeneratedMessage, GeneratedMessageCompanion }

@silent("deprecated")
@ApiMayChange
class ScalapbProtobufSerializer[T <: GeneratedMessage](companion: GeneratedMessageCompanion[T])
    extends ProtobufSerializer[T] {
  override val format: ProtobufSerialization = Protobuf
  override def serialize(t: T): ByteString = ByteString.fromArrayUnsafe(t.toByteArray)
  override def deserialize(bytes: InputStream): T = companion.parseFrom(bytes)
}
