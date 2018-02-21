package akka.http.grpc

import akka.grpc.gen.CodeGenerator
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.compiler.PluginProtos
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }

import scala.collection.JavaConverters._

class ScalaServerCodeGenerator extends CodeGenerator {

  def name = "grpc-akka-scaladsl"

  override def run(request: CodeGeneratorRequest): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder

    // FIXME generate services code here, the data types we want to leave to scalapb

    val fileDescByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
        case (acc, fp) =>
          val deps = fp.getDependencyList.asScala.map(acc).toArray
          acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps))
      }

    request.getFileToGenerateList.asScala.foreach { name ⇒
      val fileDesc = fileDescByName(name)
      val responseFile = generateFile(fileDesc)
      b.addFile(responseFile)
    }

    b.build()
  }

  def generateFile(fileDesc: FileDescriptor): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()

    //    b.setName(s"${fileDesc.scalaDirectory}/${fileDesc.fileDescriptorObjectName}Foo.scala")
    //    val fp = FunctionalPrinter()
    //      .add(s"package ${fileDesc.scalaPackageName}")
    //      .add("")
    //      .print(fileDesc.getMessageTypes.asScala) {
    //        case (p, m) =>
    //          p.add(s"object ${m.getName}Boo {")
    //            .indent
    //            .add(s"type T = ${m.scalaTypeName}")
    //            .add(s"val FieldCount = ${m.getFields.size}")
    //            .outdent
    //            .add("}")
    //      }
    //    b.setContent(fp.result)
    b.setName(s"${fileDesc.getFullName}AkkaGrpcService.scala") // FIXME
    b.setContent(s"// Hello: ${fileDesc.getFullName}")
    b.build
  }
}
