package akka.http.grpc

import akka.grpc.gen.{ CodeGenerator, BuildInfo }
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import protocbridge.Artifact
import templates.ScalaServer.txt.{ ApiTrait, Handler }

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
      serviceDesc ← fileDesc.getServices.asScala
      service = Service(fileDesc, serviceDesc)
      file ← Seq(generateServiceFile(service), generateHandler(service))
    } {
      b.addFile(file)
    }

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

  override val suggestedDependencies = Seq(
    Artifact("com.typesafe.akka", "akka-stream_2.12", "2.5.10"),
    Artifact("com.typesafe.akka", "akka-http_2.12", "10.1.0-RC2"),
    Artifact("com.trueaccord.scalapb", "scalapb-runtime_2.12", com.trueaccord.scalapb.compiler.Version.scalapbVersion),
    Artifact("io.grpc", "grpc-core", com.trueaccord.scalapb.compiler.Version.grpcJavaVersion),
    Artifact("io.grpc", "grpc-netty", com.trueaccord.scalapb.compiler.Version.grpcJavaVersion),
    Artifact("com.lightbend.akka.grpc", "akka-grpc-server_2.12", BuildInfo.version))

}
