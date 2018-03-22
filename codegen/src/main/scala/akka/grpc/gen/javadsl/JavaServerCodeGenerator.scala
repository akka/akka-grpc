package akka.grpc.gen.javadsl

import scala.collection.JavaConverters._
import akka.grpc.gen.{BuildInfo, CodeGenerator}
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import protocbridge.Artifact
import templates.JavaServer.txt.{ApiInterface, Handler}

object JavaServerCodeGenerator extends CodeGenerator {
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
      file ← Seq(
        generateServiceInterface(service),
        generateHandlerFactory(service),
      )
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

  def generateHandlerFactory(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}HandlerFactory.java")
    b.build
  }

  override val suggestedDependencies = Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version),
  )
}
