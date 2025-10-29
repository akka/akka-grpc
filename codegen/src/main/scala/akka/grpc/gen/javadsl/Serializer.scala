/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import com.google.protobuf.Descriptors.{ Descriptor, MethodDescriptor }

final case class Serializer(name: String, init: String, messageType: String)

object Serializer {
  def apply(method: MethodDescriptor, messageType: Descriptor): Serializer = {
    val name = if (method.getFile.getPackage == messageType.getFile.getPackage) {
      messageType.getName + "Serializer"
    } else {
      messageType.getFile.getPackage.replace('.', '_') + "_" + messageType.getName + "Serializer"
    }
    Serializer(
      name,
      s"new GoogleProtobufSerializer<>(${Method.getMessageType(messageType)}.parser())",
      Method.getMessageType(messageType))
  }
}
