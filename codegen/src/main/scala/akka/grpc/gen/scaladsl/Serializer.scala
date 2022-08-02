/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import com.google.protobuf.Descriptors.{ Descriptor, MethodDescriptor }
import scalapb.compiler.DescriptorImplicits

case class Serializer(name: String, init: String)

object Serializer {
  def apply(method: MethodDescriptor, messageType: Descriptor)(implicit ops: DescriptorImplicits): Serializer = {
    val name = if (method.getFile.getPackage == messageType.getFile.getPackage) {
      messageType.getName + "Serializer"
    } else {
      messageType.getFile.getPackage.replace('.', '_') + "_" + messageType.getName + "Serializer"
    }
    Serializer(name, s"new ScalapbProtobufSerializer(${Method.messageType(messageType)}.messageCompanion)")
  }
}
