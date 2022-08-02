/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
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
    options: com.google.protobuf.DescriptorProtos.MethodOptions,
    comment: Option[String] = None,
    methodDescriptor: MethodDescriptor)(implicit ops: DescriptorImplicits) {
  import Method._

  def deserializer = Serializer(methodDescriptor, inputType)
  def serializer = Serializer(methodDescriptor, outputType)

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

  val nameSafe: String =
    if (ReservedWords.contains(name)) s"""`$name`"""
    else name

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
      descriptor.getOptions,
      descriptor.comment,
      descriptor)
  }

  private def methodName(name: String) =
    name.head.toLower +: name.tail

  def messageType(messageType: Descriptor)(implicit ops: DescriptorImplicits) = {
    import ops._
    messageType.scalaType.fullName
  }

  // https://github.com/scalapb/ScalaPB/blob/master/compiler-plugin/src/main/scala/scalapb/compiler/DescriptorImplicits.scala#L1038
  private val ReservedWords = Set(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "enum",
    "extends",
    "false",
    "final",
    "finally",
    "for",
    "forSome",
    "if",
    "implicit",
    "import",
    "lazy",
    "macro",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "then",
    "this",
    "throw",
    "trait",
    "try",
    "true",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield",
    "ne")
}
