/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.collection.JavaConverters._
import scala.collection.immutable
import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import scalapb.compiler.GeneratorParams
import protocbridge.Artifact
import templates.ScalaCommon.txt._

abstract class ScalaCodeGenerator extends CodeGenerator {
  // Override this to add generated files per service
  def perServiceContent: Set[(Logger, Service) ⇒ CodeGeneratorResponse.File] = Set.empty

  // Override these to add service-independent generated files
  def staticContent(logger: Logger): Set[CodeGeneratorResponse.File] = Set.empty
  def staticContent(logger: Logger, allServices: Seq[Service]): Set[CodeGeneratorResponse.File] = Set.empty

  override def suggestedDependencies = (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) => Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix, BuildInfo.version))

  // generate services code here, the data types we want to leave to scalapb
  override def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder
    val fileDescByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
        case (acc, fp) =>
          val deps = fp.getDependencyList.asScala.map(acc).toArray
          acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps))
      }

    val services =
      (for {
        file ← request.getFileToGenerateList.asScala
        fileDesc = fileDescByName(file)
        serviceDesc ← fileDesc.getServices.asScala
      } yield Service(parseParameters(request.getParameter), fileDesc, serviceDesc)).toSeq

    for {
      service <- services
      generator ← perServiceContent
    } {
      b.addFile(generator(logger, service))
    }

    staticContent(logger).map(b.addFile)
    staticContent(logger, services).map(b.addFile)

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
  val generateServiceFile: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiTrait(service).body)
    b.setName(s"${service.packageDir}/${service.name}.scala")
    logger.info(s"Generating Akka gRPC service interface ${service.packageName}.${service.name}")
    b.build
  }
}