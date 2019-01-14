/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.JavaServer.txt.{ Handler, PowerApiInterface }

case class JavaServerCodeGenerator(powerApis: Boolean = false) extends JavaCodeGenerator {
  import JavaServerCodeGenerator._
  override def name = "akka-grpc-javadsl-server"

  override def perServiceContent: Set[(Logger, Service) ⇒ CodeGeneratorResponse.File] = super.perServiceContent ++ {
    if (powerApis) Set(generatePowerService) else Set.empty
  } + generateHandlerFactory(powerApis)

  override val suggestedDependencies = (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) => Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix, BuildInfo.version))
}

object JavaServerCodeGenerator {
  val generatePowerService: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(PowerApiInterface(service).body)
    b.setName(s"${service.packageDir}/${service.name}PowerApi.java")
    logger.info(s"Generating Akka gRPC file ${b.getName}")
    b.build
  }

  def generateHandlerFactory(powerApis: Boolean = false): (Logger, Service) ⇒ CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service, powerApis).body)
    val powerApiSuffix = if (powerApis) "PowerApi" else ""
    val serverPath = s"${service.packageDir}/${service.name}${powerApiSuffix}HandlerFactory.java"
    b.setName(serverPath)
    b.build
  }
}
