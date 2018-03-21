package akka.grpc.gen.javadsl

import akka.grpc.gen._
import com.google.protobuf.Descriptors.{Descriptor, MethodDescriptor}

final case class Method(name: String,
                  grpcName: String,
                  inputType: Descriptor,
                  inputStreaming: Boolean,
                  outputType: Descriptor,
                  outputStreaming: Boolean) {
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
    if (inputStreaming) s"Source<${getMessageType(inputType)}, NotUsed>"
    else getMessageType(inputType)

  def getReturnType =
    if (outputStreaming) s"Source<${getMessageType(outputType)}, NotUsed>"
    else s"CompletionStage<${getMessageType(outputType)}>"
}


object Method {
  def apply(descriptor: MethodDescriptor): Method = {
    Method(
      name = methodName(descriptor.getName),
      grpcName = descriptor.getName,
      descriptor.getInputType,
      descriptor.toProto.getClientStreaming,
      descriptor.getOutputType,
      descriptor.toProto.getServerStreaming,
    )
  }

  private def methodName(name: String) =
    name.head.toLower +: name.tail

  def messageType(t: Descriptor) =
    "_root_." + t.getFile.getOptions.getJavaPackage + "." + protoName(t) + "." + t.getName

  /** Java API */
  def getMessageType(t: Descriptor) = {
    t.getFile.getOptions.getJavaPackage + "." + outerClass(t) + t.getName
  }

  private def outerClass(t: Descriptor) = {
    if (t.getFile.toProto.getOptions.getJavaMultipleFiles) ""
    else {
      val outerClassName = t.getFile.toProto.getOptions.getJavaOuterClassname
      if (outerClassName == "") {
        protoName(t).head.toUpper + protoName(t).tail + "."
      } else {
        outerClassName + "."
      }
    }
  }

  private def protoName(t: Descriptor) =
    t.getFile.getName.replaceAll("\\.proto", "").split("/").last
}
