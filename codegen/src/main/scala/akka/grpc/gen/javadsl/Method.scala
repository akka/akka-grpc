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
        new DescriptorImplicits(GeneratorParams(), descriptor.getFile.getDependencies.asScala :+ descriptor.getFile)
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

}
