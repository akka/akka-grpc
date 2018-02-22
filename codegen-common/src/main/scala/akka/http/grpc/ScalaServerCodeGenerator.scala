package akka.http.grpc

import akka.grpc.gen.CodeGenerator
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import com.trueaccord.scalapb.compiler.FunctionalPrinter

import scala.collection.JavaConverters._

class ScalaServerCodeGenerator extends CodeGenerator {
  def name = "grpc-akka-scaladsl"

  override def run(request: CodeGeneratorRequest): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder

    // generate services code here, the data types we want to leave to scalapb

    val fileDescByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
        case (acc, fp) =>
          val deps = fp.getDependencyList.asScala.map(acc).toArray
          acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps))
      }

    for {
      file ← request.getFileToGenerateList.asScala
      fileDesc = fileDescByName(file)
      service ← fileDesc.getServices.asScala
      file = generateServiceFile(fileDesc, service)
    } {
      b.addFile(file)
    }

    b.build()
  }

  def generateServiceFile(fileDesc: FileDescriptor, serviceDescriptor: ServiceDescriptor): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()

    val methods = serviceDescriptor.getMethods.asScala

    // https://scalapb.github.io/generated-code.html for more subtleties
    val pack = fileDesc.getOptions.getJavaPackage + "." + fileDesc.getName.replaceAll("\\.proto", "").split("/").last

    val serviceClassName = serviceDescriptor.getName + "Service"
    val fp = FunctionalPrinter()
      .add(s"package $pack")
      .add("")
      .add("import scala.concurrent.Future")
      .add("")
      .add("import akka.NotUsed")
      .add("import akka.stream.scaladsl.Source")
      .add("")
      .add(s"trait $serviceClassName {")
      .indent
      .print(methods) {
        case (p, m) ⇒
          val in =
            if (m.toProto.getClientStreaming) s"Source[${messageType(m.getInputType)}, NotUsed]"
            else messageType(m.getInputType)
          val out =
            if (m.toProto.getServerStreaming) s"Source[${messageType(m.getOutputType)}, Any]"
            else s"Future[${messageType(m.getOutputType)}]"
          p.add(s"def ${methodName(m.getName)}(in: $in): $out").add("")
      }
      .outdent
      .add("}")
    b.setContent(fp.result)
    b.setName(s"${pack.replace('.', '/')}/$serviceClassName.scala")
    println(s"Creating in $pack")
    b.build
  }

  def methodName(name: String) =
    name.head.toLower +: name.tail

  def messageType(t: Descriptor) =
    t.getFile.getOptions.getJavaPackage + "." + t.getFile.getName.replaceAll("\\.proto", "").split("/").last + "." + t.getName
}
