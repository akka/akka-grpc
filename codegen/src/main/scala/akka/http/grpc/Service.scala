package akka.http.grpc

import scala.collection.JavaConverters._

import com.google.protobuf.Descriptors._

case class Serializer(name: String, init: String, javaInit: String, javaMessageType: String) {
  /** Java API */
  def getInit() = javaInit

  /** Java API */
  def getMessageType() = javaMessageType
}
object Serializer {
  def apply(t: Descriptor): Serializer = Serializer(
    t.getName + "Serializer",
    s"new ScalapbProtobufSerializer(${Method.messageType(t)}.messageCompanion)",
    s"new GoogleProtobufSerializer<>(${Method.getMessageType(t)}.class)",
    Method.getMessageType(t)
  )
}


sealed trait MethodType
case object Unary extends MethodType
case object ClientStreaming extends MethodType
case object ServerStreaming extends MethodType
case object BidiStreaming extends MethodType

case class Method(name: String,
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

  def parameterType =
    if (inputStreaming) s"Source[${messageType(inputType)}, _]"
    else messageType(inputType)

  def inputTypeUnboxed = messageType(inputType)
  def outputTypeUnboxed = messageType(outputType)

  def returnType =
    if (outputStreaming) s"Source[${messageType(outputType)}, Any]"
    else s"Future[${messageType(outputType)}]"


  val methodType: MethodType = {
    (inputStreaming, outputStreaming) match {
      case (false, false) => Unary
      case (true, false)  => ClientStreaming
      case (false, true)  => ServerStreaming
      case (true, true)   => BidiStreaming
    }
  }


  /** Java API */
  def getParameterType =
    if (inputStreaming) s"Source<${getMessageType(inputType)}, Object>"
    else getMessageType(inputType)

  /** Java API */
  def getReturnType =
    if (outputStreaming) s"Source<${getMessageType(outputType)}, Object>"
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

case class Service(packageName: String, javaPackageName: String, name: String, grpcName: String, methods: Seq[Method]) {
  def serializers: Set[Serializer] = (methods.map(_.deserializer) ++ methods.map(_.serializer)).toSet

  /** Java API */
  def getPackageName() = javaPackageName
}
object Service {
  def apply(fileDesc: FileDescriptor, serviceDescriptor: ServiceDescriptor): Service = {
    // https://scalapb.github.io/generated-code.html for more subtleties
    val packageName = fileDesc.getOptions.getJavaPackage + "." + fileDesc.getName.replaceAll("\\.proto", "").split("/").last
    val javaPackageName = fileDesc.getOptions.getJavaPackage

    val serviceClassName = serviceDescriptor.getName + "Service"

    Service(
      packageName,
      javaPackageName,
      serviceClassName,
      fileDesc.getPackage + "." + serviceDescriptor.getName,
      serviceDescriptor.getMethods.asScala.map(method ⇒ Method(method)))
  }
}
