package akka.http.grpc.scaladsl

import com.google.protobuf.Descriptors.Descriptor
import com.trueaccord.scalapb.compiler.DescriptorPimps

case class Serializer(name: String, init: String)

object Serializer {
  def apply(messageType: Descriptor)(implicit ops: DescriptorPimps): Serializer = Serializer(
    messageType.getName + "Serializer",
    s"new ScalapbProtobufSerializer(${Method.messageType(messageType)}.messageCompanion)")
}