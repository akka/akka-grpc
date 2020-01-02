/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.collection.JavaConverters._
import scala.collection.immutable
import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import scalapb.compiler.GeneratorParams
import protocbridge.Artifact

abstract class ScalaCodeGenerator extends CodeGenerator {
  // Override this to add generated files per service
  def perServiceContent: Set[(Logger, Service) => immutable.Seq[CodeGeneratorResponse.File]] = Set.empty

  // Override these to add service-independent generated files
  def staticContent(logger: Logger): Set[CodeGeneratorResponse.File] = Set.empty
  def staticContent(logger: Logger, allServices: Seq[Service]): Set[CodeGeneratorResponse.File] = Set.empty

  override def suggestedDependencies =
    (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) =>
      Seq(
        Artifact(
          BuildInfo.organization,
          BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix,
          BuildInfo.version))

  // generate services code here, the data types we want to leave to scalapb
  override def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder
    val fileDescByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
        case (acc, fp) =>
          val deps = fp.getDependencyList.asScala.map(acc).toArray
          acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps))
      }

    // Currently per-invocation options, intended to become per-service options eventually
    // https://github.com/akka/akka-grpc/issues/451
    val params = request.getParameter.toLowerCase
    // flags listed in akkaGrpcCodeGeneratorSettings's description
    val serverPowerApi = params.contains("server_power_apis") && !params.contains("server_power_apis=false")
    val usePlayActions = params.contains("use_play_actions") && !params.contains("use_play_actions=false")

    val services =
      (for {
        file <- request.getFileToGenerateList.asScala
        fileDesc = fileDescByName(file)
        serviceDesc <- fileDesc.getServices.asScala
      } yield Service(parseParameters(request.getParameter), fileDesc, serviceDesc, serverPowerApi, usePlayActions)).toSeq

    for {
      service <- services
      generator <- perServiceContent
      generated <- generator(logger, service)
    } {
      b.addFile(generated)
    }

    staticContent(logger).map(b.addFile)
    staticContent(logger, services).map(b.addFile)

    b.build()
  }

  // flags listed in akkaGrpcCodeGeneratorSettings's description
  private def parseParameters(params: String): GeneratorParams =
    params.split(",").map(_.trim).filter(_.nonEmpty).foldLeft[GeneratorParams](GeneratorParams()) {
      case (p, "java_conversions")            => p.copy(javaConversions = true)
      case (p, "flat_package")                => p.copy(flatPackage = true)
      case (p, "single_line_to_string")       => p.copy(singleLineToProtoString = true) // for backward-compatibility
      case (p, "single_line_to_proto_string") => p.copy(singleLineToProtoString = true)
      case (p, "ascii_format_to_string")      => p.copy(asciiFormatToString = true)
      case (p, "no_lenses")                   => p.copy(lenses = false)
      case (p, "retain_source_code_info")     => p.copy(retainSourceCodeInfo = true)
      case (x, _)                             => x
    }
}
