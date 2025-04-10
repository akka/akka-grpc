/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import scala.collection.immutable
import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.JavaServer.txt.ScalaHandler
import templates.JavaServer.txt.{ Handler, PowerApiInterface }

class JavaServerCodeGenerator extends JavaCodeGenerator {
  override def name = "akka-grpc-javadsl-server"

  override def perServiceContent: Set[(Logger, Service) => immutable.Seq[CodeGeneratorResponse.File]] =
    super.perServiceContent + generatePlainHandlerFactory +
    generatePowerHandlerFactory + generatePowerService + generateScalaHandlerFactory

  override val suggestedDependencies = (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) =>
    Seq(
      Artifact(
        BuildInfo.organization,
        BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix,
        BuildInfo.version))

  val generatePlainHandlerFactory: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] =
    (logger, service) => {
      if (!service.generateScalaHandlerFactory) {
        val b = CodeGeneratorResponse.File.newBuilder()
        b.setContent(Handler(service, powerApis = false).body)
        val serverPath = s"${service.packageDir}/${service.name}HandlerFactory.java"
        b.setName(serverPath)
        logger.info(s"Generating Akka gRPC service handler for ${service.packageName}.${service.name}")
        immutable.Seq(b.build)
      } else immutable.Seq.empty
    }

  val generatePowerHandlerFactory: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] =
    (logger, service) => {
      if (service.serverPowerApi) {
        val b = CodeGeneratorResponse.File.newBuilder()
        b.setContent(Handler(service, powerApis = true).body)
        val serverPath = s"${service.packageDir}/${service.name}PowerApiHandlerFactory.java"
        b.setName(serverPath)
        logger.info(s"Generating Akka gRPC service power API handler for ${service.packageName}.${service.name}")
        immutable.Seq(b.build)
      } else immutable.Seq.empty
    }

  val generatePowerService: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    if (service.serverPowerApi) {
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(PowerApiInterface(service).body)
      b.setName(s"${service.packageDir}/${service.name}PowerApi.java")
      logger.info(s"Generating Akka gRPC service power interface for [${service.packageName}.${service.name}]")
      immutable.Seq(b.build)
    } else immutable.Seq.empty
  }

  val generateScalaHandlerFactory: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] =
    (logger, service) => {
      if (service.generateScalaHandlerFactory) {
        val b = CodeGeneratorResponse.File.newBuilder()
        b.setContent(ScalaHandler(service).body)
        val serverPath = s"${service.packageDir}/${service.name}ScalaHandlerFactory.java"
        b.setName(serverPath)
        logger.info(
          s"Generating Akka gRPC instance per request service handler for ${service.packageName}.${service.name}")
        immutable.Seq(b.build)
      } else
        immutable.Seq.empty
    }
}
object JavaServerCodeGenerator extends JavaServerCodeGenerator
