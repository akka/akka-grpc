/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import scalapb.compiler.GeneratorParams
import protocbridge.Artifact
import com.google.protobuf.ExtensionRegistry
import protocgen.CodeGenRequest
import scalapb.options.Scalapb

abstract class ScalaCodeGenerator extends CodeGenerator {

  // Override this to add generated files per service
  def perServiceContent: Set[(Logger, Service) => immutable.Seq[CodeGeneratorResponse.File]] = Set.empty

  // Override these to add service-independent generated files
  def staticContent(@nowarn("cat=unused-params") logger: Logger): Set[CodeGeneratorResponse.File] = Set.empty
  def staticContent(
      @nowarn("cat=unused-params") logger: Logger,
      @nowarn("cat=unused-params") allServices: Seq[Service]): Set[CodeGeneratorResponse.File] = Set.empty

  override def suggestedDependencies =
    (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) =>
      Seq(
        Artifact(
          BuildInfo.organization,
          BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix,
          BuildInfo.version))

  override def registerExtensions(registry: ExtensionRegistry): Unit = {
    // Allow the embedded ScalaPB compiler helper classes to observe package/file-level options,
    // so that properties looked up through DescriptorImplicits are in line with ScalaPB generated stubs
    Scalapb.registerAllExtensions(registry)
  }

  // generate services code here, the data types we want to leave to scalapb
  override def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder
    b.setSupportedFeatures(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.getNumber)

    // Currently per-invocation options, intended to become per-service options eventually
    // https://github.com/akka/akka-grpc/issues/451
    val params = request.getParameter.toLowerCase
    // flags listed in akkaGrpcCodeGeneratorSettings's description
    val serverPowerApi = params.contains("server_power_apis") && !params.contains("server_power_apis=false")
    val usePlayActions = params.contains("use_play_actions") && !params.contains("use_play_actions=false")

    val codeGenRequest = CodeGenRequest(request)
    val services =
      (for {
        fileDesc <- codeGenRequest.filesToGenerate
        serviceDesc <- fileDesc.getServices.asScala
      } yield Service(
        codeGenRequest,
        parseParameters(request.getParameter),
        fileDesc,
        serviceDesc,
        serverPowerApi,
        usePlayActions)).toSeq

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
      case (p, "grpc")                        => p.copy(grpc = true)
      case (x, _)                             => x
    }
}
