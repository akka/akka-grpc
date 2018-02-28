package akka.http.grpc

import akka.grpc.gen.{ CodeGenerator, BuildInfo }
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import templates.JavaServer.txt.ApiInterface

import scala.collection.JavaConverters._

import protocbridge.Artifact

class JavaServerCodeGenerator extends CodeGenerator {
  def name = "akka-grpc-javadsl"

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
      serviceDesc ← fileDesc.getServices.asScala
      service = Service(fileDesc, serviceDesc)
      file ← Seq(generateServiceInterface(service))
    } {
      b.addFile(file)
    }

    b.build()
  }

  def generateServiceInterface(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiInterface(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}.java")
    b.build
  }

  override val suggestedDependencies = Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version),
  )
}
