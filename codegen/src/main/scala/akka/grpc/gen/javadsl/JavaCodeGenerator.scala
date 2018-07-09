/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.{BuildInfo, CodeGenerator}
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import protocbridge.Artifact
import templates.JavaCommon.txt.ApiInterface

import scala.collection.JavaConverters._

abstract class JavaCodeGenerator extends CodeGenerator {
  // Override this to add generated files per service
  def perServiceContent = Set[Service ⇒ CodeGeneratorResponse.File](generateServiceInterface)

  // Override this to add service-independent generated files
  def staticContent = Set.empty[CodeGeneratorResponse.File]

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
      generator ← perServiceContent
    } {
      b.addFile(generator(service))
    }

    staticContent.map(b.addFile)

    b.build()
  }

  def generateServiceInterface(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiInterface(service).body)
    b.setName(s"${service.packageDir}/${service.name}.java")
    b.build
  }

  override val suggestedDependencies = Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version),
  )
}
