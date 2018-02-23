package akka.http.grpc

import scala.collection.JavaConverters._

import com.google.protobuf.Descriptors._

case class Method(name: String, parameterType: String, returnType: String)
object Method {
  def apply(descriptor: MethodDescriptor): Method = {
    Method(
      name = methodName(descriptor.getName),
      parameterType(descriptor.toProto.getClientStreaming, descriptor.getInputType),
      returnType(descriptor.toProto.getServerStreaming, descriptor.getOutputType),
    )
  }

  private def methodName(name: String) =
    name.head.toLower +: name.tail

  private def parameterType(streaming: Boolean, t: Descriptor) =
    if (streaming) s"Source[${messageType(t)}, NotUsed]"
    else messageType(t)

  private def returnType(streaming: Boolean, t: Descriptor) =
    if (streaming) s"Source[${messageType(t)}, Any]"
    else s"Future[${messageType(t)}]"

  private def messageType(t: Descriptor) =
    "_root_." + t.getFile.getOptions.getJavaPackage + "." + t.getFile.getName.replaceAll("\\.proto", "").split("/").last + "." + t.getName
}

case class Service(packageName: String, name: String, methods: Seq[Method]) {
  def filename = s"${packageName.replace('.', '/')}/$name.scala"
}
object Service {
  def apply(fileDesc: FileDescriptor, serviceDescriptor: ServiceDescriptor): Service = {
    // https://scalapb.github.io/generated-code.html for more subtleties
    val packageName = fileDesc.getOptions.getJavaPackage + "." + fileDesc.getName.replaceAll("\\.proto", "").split("/").last

    val serviceClassName = serviceDescriptor.getName + "Service"

    Service(packageName, serviceClassName, serviceDescriptor.getMethods.asScala.map(method ⇒ Method(method)))
  }
}