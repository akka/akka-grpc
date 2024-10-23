/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import com.google.protobuf.Descriptors.{ Descriptor, MethodDescriptor }
import scalapb.compiler.DescriptorImplicits

case class Serializer(name: String, init: String, messageClass: String)

object Serializer {
  def apply(method: MethodDescriptor, messageType: Descriptor)(implicit ops: DescriptorImplicits): Serializer = {
    val name = if (method.getFile.getPackage == messageType.getFile.getPackage) {
      messageType.getName + "Serializer"
    } else {
      messageType.getFile.getPackage.replace('.', '_') + "_" + messageType.getName + "Serializer"
    }
    val messageClass = Method.messageType(messageType)
    Serializer(name, s"new ScalapbProtobufSerializer($messageClass.messageCompanion)", messageClass)
  }
}
