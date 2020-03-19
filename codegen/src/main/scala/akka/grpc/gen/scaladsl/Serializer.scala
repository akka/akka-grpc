/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import com.google.protobuf.Descriptors.Descriptor
import scalapb.compiler.DescriptorImplicits

case class Serializer(name: String, init: String, jsonInit: String)

object Serializer {
  def apply(messageType: Descriptor)(implicit ops: DescriptorImplicits): Serializer =
    Serializer(
      messageType.getName + "Serializers",
      s"new ScalapbProtobufSerializer(${Method.messageType(messageType)}.messageCompanion)",
      s"new ScalapbJsonProtobufSerializer(${Method.messageType(messageType)}.messageCompanion, typeRegistry)")
}
