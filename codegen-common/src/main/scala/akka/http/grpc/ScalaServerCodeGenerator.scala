package akka.http.grpc

import akka.grpc.gen.CodeGenerator
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import templates.ScalaServer.txt.ApiTrait

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
    val service = Service(fileDesc, serviceDescriptor)

    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiTrait(service).body)
    b.setName(service.filename)
    b.build
  }

}
