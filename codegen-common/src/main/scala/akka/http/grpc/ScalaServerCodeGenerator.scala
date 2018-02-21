package akka.http.grpc

import akka.grpc.gen.CodeGenerator
import com.google.protobuf.Descriptors.{ FileDescriptor, ServiceDescriptor }
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

    val serviceClassName = serviceDescriptor.getName + "Service"
    val fp = FunctionalPrinter()
      .add(s"package ${fileDesc.getOptions.getJavaPackage}.${fileDesc.getPackage}")
      .add("")
      .add("import scala.concurrent.Future")
      .add("")
      .add("import akka.NotUsed")
      .add("import akka.stream.scaladsl.Source")
      .add("")
      .add(s"trait $serviceClassName {")
      .indent
      .print(serviceDescriptor.getMethods.asScala) {
        case (p, m) ⇒
          val in =
            if (m.toProto.getClientStreaming) s"Source[${m.getInputType.getName}, NotUsed]"
            else m.getInputType.getName
          val out =
            if (m.toProto.getServerStreaming) s"Source[${m.getInputType.getName}, Any]"
            else s"Future[${m.getInputType.getName}]"
          p.add(s"def ${methodName(m.getName)}(in: $in): $out").add("")
      }
      .outdent
      .add("}")
    b.setContent(fp.result)
    b.setName(s"${fileDesc.getOptions.getJavaPackage.replace('.', '/')}/${fileDesc.getPackage}/$serviceClassName.scala")

    b.build
  }

  def methodName(name: String) = name.head.toLower +: name.tail
}
