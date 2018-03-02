package akka.http.grpc

import akka.grpc.gen.{ CodeGenerator, BuildInfo }
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import protocbridge.Artifact
import templates.ScalaServer.txt._

import scala.collection.JavaConverters._

object ScalaServerCodeGenerator extends CodeGenerator {
  def name = "akka-grpc-scaladsl"

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
      file ← Seq(generateServiceFile(service), generateHandler(service), generateStub(service))
    } {
      b.addFile(file)
    }

    b.addFile(generateGuavaConverters())
    b.addFile(generateClientMarshaller())
    b.build()
  }

  def generateServiceFile(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiTrait(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}.scala")
    b.build
  }

  def generateHandler(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}Handler.scala")
    b.build
  }

  def generateStub(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Client(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}Client.scala")
    b.build
  }

  def generateGuavaConverters(): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    val packageName = "akka.http.grpc"
    b.setContent(GuavaConverters().body)
    b.setName(s"${packageName.replace('.', '/')}/GuavaConverters.scala")
    b.build
  }

  def generateClientMarshaller(): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    val packageName = "akka.http.grpc"
    b.setContent(Marshaller().body)
    b.setName(s"${packageName.replace('.', '/')}/Marshaller.scala")
    b.build
  }

  override val suggestedDependencies = Seq(Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version))
}
