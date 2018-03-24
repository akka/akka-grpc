/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import com.google.protobuf.Descriptors.Descriptor
import scalapb.compiler.DescriptorPimps

case class Serializer(name: String, init: String)

object Serializer {
  def apply(messageType: Descriptor)(implicit ops: DescriptorPimps): Serializer = Serializer(
    messageType.getName + "Serializer",
    s"new ScalapbProtobufSerializer(${Method.messageType(messageType)}.messageCompanion)")
}