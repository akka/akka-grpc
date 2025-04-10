/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import protocbridge.Artifact
import templates.JavaCommon.txt.ApiInterface

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import protocgen.CodeGenRequest

abstract class JavaCodeGenerator extends CodeGenerator {

  /** Override this to add generated files per service */
  def perServiceContent: Set[(Logger, Service) => immutable.Seq[CodeGeneratorResponse.File]] = Set.empty

  /** Override these to add service-independent generated files */
  def staticContent(@nowarn("cat=unused-params") logger: Logger): Set[CodeGeneratorResponse.File] =
    Set.empty
  def staticContent(
      @nowarn("cat=unused-params") logger: Logger,
      @nowarn("cat=unused-params") allServices: Seq[Service]): Set[CodeGeneratorResponse.File] =
    Set.empty

  override def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder
    b.setSupportedFeatures(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.getNumber)

    // generate services code here, the data types we want to leave to scalapb

    // Currently per-invocation options, intended to become per-service options eventually
    // https://github.com/akka/akka-grpc/issues/451
    val params = request.getParameter.toLowerCase
    def paramEnabled(name: String): Boolean =
      params.contains(name) && !params.contains(s"$name=false")

    val serverPowerApi = paramEnabled("server_power_apis")
    val usePlayActions = paramEnabled("use_play_actions")
    val generateScalaHandlerFactory = paramEnabled("generate_scala_handler_factory")
    val generateBlockingApis = paramEnabled("blocking_apis")

    if (serverPowerApi && generateScalaHandlerFactory) {
      logger.warn(
        "Both server_power_apis and generate_scala_handler_factory enabled. Handler for power API not supported and will not be generated")
    }
    if (generateBlockingApis && (serverPowerApi || usePlayActions)) {
      val ex = new IllegalArgumentException(
        "blocking_apis can only be combined with generate_scala_handler_factory, no other option")
      logger.error(ex.getMessage)
      throw ex
    }
    if (generateBlockingApis && !generateScalaHandlerFactory) {
      val ex = new IllegalArgumentException("blocking_apis must be combined with generate_scala_handler_factory")
      logger.error(ex.getMessage)
      throw ex
    }

    val codeGenRequest = CodeGenRequest(request)
    val services = (for {
      fileDesc <- codeGenRequest.filesToGenerate
      serviceDesc <- fileDesc.getServices.asScala
    } yield Service(
      codeGenRequest,
      fileDesc,
      serviceDesc,
      serverPowerApi,
      usePlayActions,
      asyncReturnValues = !generateBlockingApis,
      generateScalaHandlerFactory = generateScalaHandlerFactory)).toVector

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
