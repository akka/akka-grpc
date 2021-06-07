/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import protocbridge.Artifact
import templates.JavaCommon.txt.ApiInterface

import scala.collection.JavaConverters._
import scala.collection.immutable
import com.github.ghik.silencer.silent
import protocgen.CodeGenRequest

abstract class JavaCodeGenerator extends CodeGenerator {

  /** Override this to add generated files per service */
  def perServiceContent: Set[(Logger, Service) => immutable.Seq[CodeGeneratorResponse.File]] = Set.empty

  /** Override these to add service-independent generated files */
  def staticContent(@silent("never used") logger: Logger): Set[CodeGeneratorResponse.File] =
    Set.empty
  def staticContent(
      @silent("never used") logger: Logger,
      @silent("never used") allServices: Seq[Service]): Set[CodeGeneratorResponse.File] =
    Set.empty

  override def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder
    b.setSupportedFeatures(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.getNumber)

    // generate services code here, the data types we want to leave to scalapb

    // Currently per-invocation options, intended to become per-service options eventually
    // https://github.com/akka/akka-grpc/issues/451
    val params = request.getParameter.toLowerCase
    val serverPowerApi = params.contains("server_power_apis") && !params.contains("server_power_apis=false")
    val usePlayActions = params.contains("use_play_actions") && !params.contains("use_play_actions=false")

    val codeGenRequest = CodeGenRequest(request)
    val services = (for {
      fileDesc <- codeGenRequest.filesToGenerate
      serviceDesc <- fileDesc.getServices.asScala
    } yield Service(codeGenRequest, fileDesc, serviceDesc, serverPowerApi, usePlayActions)).toVector

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

  def generateServiceInterface(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiInterface(service).body)
    b.setName(s"${service.packageDir}/${service.name}.java")
    b.build
  }

  override val suggestedDependencies = (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) =>
    Seq(
      Artifact(
        BuildInfo.organization,
        BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix,
        BuildInfo.version))
}
