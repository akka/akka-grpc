/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import com.google.protobuf.Descriptors.{ Descriptor, MethodDescriptor }
import akka.grpc.gen._
import scalapb.compiler.DescriptorImplicits

case class Method(
    name: String,
    grpcName: String,
    inputType: Descriptor,
    inputStreaming: Boolean,
    outputType: Descriptor,
    outputStreaming: Boolean,
    comment: Option[String] = None)(implicit ops: DescriptorImplicits) {
  import Method._

  def deserializer = Serializer(inputType)
  def serializer = Serializer(outputType)

  def unmarshal =
    if (inputStreaming) "GrpcMarshalling.unmarshalStream"
    else "GrpcMarshalling.unmarshal"

  def marshal =
    if (outputStreaming) "GrpcMarshalling.marshalStream"
    else "GrpcMarshalling.marshal"

  def parameterType =
    if (inputStreaming) s"akka.stream.scaladsl.Source[${messageType(inputType)}, akka.NotUsed]"
    else messageType(inputType)

  def inputTypeUnboxed = messageType(inputType)
  def outputTypeUnboxed = messageType(outputType)

  def returnType =
    if (outputStreaming) s"akka.stream.scaladsl.Source[${messageType(outputType)}, akka.NotUsed]"
    else s"scala.concurrent.Future[${messageType(outputType)}]"

  val methodType: MethodType = {
    (inputStreaming, outputStreaming) match {
      case (false, false) => Unary
      case (true, false)  => ClientStreaming
      case (false, true)  => ServerStreaming
      case (true, true)   => BidiStreaming
    }
  }
}

object Method {
  def apply(descriptor: MethodDescriptor)(implicit ops: DescriptorImplicits): Method = {
    import ops._
    Method(
      name = methodName(descriptor.getName),
      grpcName = descriptor.getName,
      descriptor.getInputType,
      descriptor.toProto.getClientStreaming,
      descriptor.getOutputType,
      descriptor.toProto.getServerStreaming,
      descriptor.comment)
  }

  private def methodName(name: String) =
    name.head.toLower +: name.tail

  def messageType(messageType: Descriptor)(implicit ops: DescriptorImplicits) = {
    import ops._
    messageType.scalaTypeName
  }

  private def outerClass(t: Descriptor) =
    if (t.getFile.toProto.getOptions.getJavaMultipleFiles) ""
    else {
      val outerClassName = t.getFile.toProto.getOptions.getJavaOuterClassname
      if (outerClassName == "") {
        protoName(t).head.toUpper + protoName(t).tail + "."
      } else {
        outerClassName + "."
      }
    }

  private def protoName(t: Descriptor) =
    t.getFile.getName.replaceAll("\\.proto", "").split("/").last
}
