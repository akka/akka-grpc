package akka.http.grpc.scaladsl

import scala.collection.JavaConverters._
import akka.grpc.gen.{ BuildInfo, CodeGenerator }
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import com.trueaccord.scalapb.compiler.{ GeneratorParams, ProtobufGenerator }
import protocbridge.Artifact
import templates.ScalaServer.txt._

object ScalaServerCodeGenerator extends CodeGenerator {
  def name = "akka-grpc-scaladsl"

  // generate services code here, the data types we want to leave to scalapb
  override def run(request: CodeGeneratorRequest): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder

    val generatorParams = parseParameters(request.getParameter)

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
      service = Service(generatorParams, fileDesc, serviceDesc)
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
    val packageName = "akka.http.grpc.internal"
    b.setContent(ChannelApiHelpers().body)
    b.setName(s"${packageName.replace('.', '/')}/ChannelApiHelpers.scala")
    b.build
  }

  def generateClientMarshaller(): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    val packageName = "akka.http.grpc.internal"
    b.setContent(Marshaller().body)
    b.setName(s"${packageName.replace('.', '/')}/Marshaller.scala")
    b.build
  }

  override val suggestedDependencies = Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version),
    // TODO: remove grpc-stub dependency once we have a akka-http based client
    Artifact("io.grpc", "grpc-stub", com.trueaccord.scalapb.compiler.Version.grpcJavaVersion))

  private def parseParameters(params: String): GeneratorParams = {
    params.split(",").map(_.trim).filter(_.nonEmpty).foldLeft[GeneratorParams](GeneratorParams()) {
      case (p, "java_conversions") => p.copy(javaConversions = true)
      case (p, "flat_package") => p.copy(flatPackage = true)
      case (p, "grpc") => p.copy(grpc = true)
      case (p, "single_line_to_string") => p.copy(singleLineToString = true)
      case (x, _) => x
    }
  }
}
