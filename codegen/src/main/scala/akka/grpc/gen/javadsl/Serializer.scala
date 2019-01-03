/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import com.google.protobuf.Descriptors.Descriptor

final case class Serializer(name: String, init: String, messageType: String)

object Serializer {
  def apply(messageType: Descriptor): Serializer = Serializer(
    messageType.getName + "Serializer",
    s"new GoogleProtobufSerializer<>(${Method.getMessageType(messageType)}.class)",
    Method.getMessageType(messageType))
}
