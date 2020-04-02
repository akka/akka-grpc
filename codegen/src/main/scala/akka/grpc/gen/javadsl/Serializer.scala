/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import com.google.protobuf.Descriptors.Descriptor

final case class Serializer(name: String, init: String, jsonInit: String, messageType: String)

object Serializer {
  def apply(messageType: Descriptor): Serializer =
    Serializer(
      messageType.getName + "Serializers",
      s"new GoogleProtobufSerializer<>(${Method.getMessageType(messageType)}.getDefaultInstance())",
      s"new GoogleJsonProtobufSerializer<>(${Method.getMessageType(messageType)}.getDefaultInstance())",
      Method.getMessageType(messageType))
}
