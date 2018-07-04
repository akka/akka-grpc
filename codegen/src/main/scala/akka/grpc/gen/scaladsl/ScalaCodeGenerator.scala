/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.collection.JavaConverters._
import scala.collection.immutable

import akka.grpc.gen.{ BuildInfo, CodeGenerator }

import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import scalapb.compiler.GeneratorParams

import protocbridge.Artifact

import templates.ScalaCommon.txt._

abstract class ScalaCodeGenerator extends CodeGenerator {
  // Override this to add generated files per service
  def perServiceContent: Set[Service ⇒ CodeGeneratorResponse.File] = Set.empty

  // Override this to add service-independent generated files
  def staticContent: Set[CodeGeneratorResponse.File] = Set.empty

  override def suggestedDependencies = Seq(Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version))

  // generate services code here, the data types we want to leave to scalapb
  override def run(request: CodeGeneratorRequest): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder

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
      service = Service(parseParameters(request.getParameter), fileDesc, serviceDesc)
      generator ← perServiceContent
    } {
      b.addFile(generator(service))
    }

    staticContent.map(b.addFile)

    b.build()
  }

  private def parseParameters(params: String): GeneratorParams = {
    params.split(",").map(_.trim).filter(_.nonEmpty).foldLeft[GeneratorParams](GeneratorParams()) {
      case (p, "java_conversions") => p.copy(javaConversions = true)
      case (p, "flat_package") => p.copy(flatPackage = true)
      case (p, "grpc") => p.copy(grpc = true)
      case (p, "single_line_to_string") => p.copy(singleLineToProtoString = true)
      case (x, _) => x
    }
  }
}
object ScalaCodeGenerator {
  val generateServiceFile: Service => CodeGeneratorResponse.File = service => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiTrait(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}.scala")
    b.build
  }
}