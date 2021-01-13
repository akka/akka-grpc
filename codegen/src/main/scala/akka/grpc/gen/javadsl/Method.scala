/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen._
import com.google.protobuf.Descriptors.{ Descriptor, MethodDescriptor }
import scalapb.compiler.{ DescriptorImplicits, GeneratorParams }

import scala.collection.JavaConverters._

final case class Method(
    name: String,
    grpcName: String,
    inputType: Descriptor,
    inputStreaming: Boolean,
    outputType: Descriptor,
    outputStreaming: Boolean,
    comment: Option[String] = None) {
  import Method._

  require(
    !ReservedWords.contains(name),
    s"The method name `$name` is a reserved word in Java, please change it in your proto")

  def deserializer = Serializer(inputType)
  def serializer = Serializer(outputType)

  def unmarshal =
    if (inputStreaming) "GrpcMarshalling.unmarshalStream"
    else "GrpcMarshalling.unmarshal"

  def marshal =
    if (outputStreaming) "GrpcMarshalling.marshalStream"
    else "GrpcMarshalling.marshal"

  def inputTypeUnboxed = getMessageType(inputType)
  def outputTypeUnboxed = getMessageType(outputType)

  val methodType: MethodType = {
    (inputStreaming, outputStreaming) match {
      case (false, false) => Unary
      case (true, false)  => ClientStreaming
      case (false, true)  => ServerStreaming
      case (true, true)   => BidiStreaming
    }
  }

  def getParameterType =
    if (inputStreaming) s"akka.stream.javadsl.Source<${getMessageType(inputType)}, akka.NotUsed>"
    else getMessageType(inputType)

  def getReturnType =
    if (outputStreaming) s"akka.stream.javadsl.Source<${getMessageType(outputType)}, akka.NotUsed>"
    else s"java.util.concurrent.CompletionStage<${getMessageType(outputType)}>"
}

object Method {
  def apply(descriptor: MethodDescriptor): Method = {
    val comment = {
      // Use ScalaPB's implicit classes to avoid replicating the logic for comment extraction
      // Note that this be problematic if/when ScalaPB uses scala-specific stuff to do that
      implicit val ops =
        new DescriptorImplicits(
          GeneratorParams(),
          descriptor.getFile.getDependencies.asScala.toList :+ descriptor.getFile)
      import ops._
      descriptor.comment
    }

    Method(
      name = methodName(descriptor.getName),
      grpcName = descriptor.getName,
      descriptor.getInputType,
      descriptor.toProto.getClientStreaming,
      descriptor.getOutputType,
      descriptor.toProto.getServerStreaming,
      comment)
  }

  private def methodName(name: String) =
    name.head.toLower +: name.tail

  def messageType(t: Descriptor) =
    "_root_." + t.getFile.getOptions.getJavaPackage + "." + Service.protoName(t.getFile) + "." + t.getName

  /** Java API */
  def getMessageType(t: Descriptor) = {
    val packageName =
      if (t.getFile.getOptions.hasJavaPackage) t.getFile.getOptions.getJavaPackage
      else t.getFile.getPackage
    val name =
      if (t.getFile.toProto.getOptions.getJavaMultipleFiles) t.getName
      else Service.outerClass(t.getFile) + "." + t.getName
    (if (packageName.isEmpty) "" else packageName + ".") + name
  }

  // https://docs.oracle.com/javase/tutorial/java/nutsandbolts/_keywords.html
  private val ReservedWords = Set(
    "abstract",
    "continue",
    "for",
    "new",
    "switch",
    "assert",
    "default",
    "goto",
    "package",
    "synchronized",
    "boolean",
    "do",
    "if",
    "private",
    "this",
    "break",
    "double",
    "implements",
    "protected",
    "throw",
    "byte",
    "else",
    "import",
    "public",
    "throws",
    "case",
    "enum",
    "instanceof",
    "return",
    "transient",
    "catch",
    "extends",
    "int",
    "short",
    "try",
    "char",
    "final",
    "interface",
    "static",
    "void",
    "class",
    "finally",
    "long",
    "strictfp",
    "volatile",
    "const",
    "float",
    "native",
    "super",
    "while")

}
