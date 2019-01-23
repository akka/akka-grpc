/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.JavaServer.txt.Handler

case class JavaServerCodeGenerator(powerApis: Boolean = false) extends JavaCodeGenerator {
  override def name = "akka-grpc-javadsl-server"

  override def perServiceContent: Set[(Logger, Service) ⇒ CodeGeneratorResponse.File] = super.perServiceContent + generateHandlerFactory(powerApis)

  override val suggestedDependencies = (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) => Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix, BuildInfo.version))

  def generateHandlerFactory(powerApis: Boolean = false): (Logger, Service) ⇒ CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service, powerApis).body)
    val powerApiSuffix = if (powerApis) "PowerApi" else ""
    val serverPath = s"${service.packageDir}/${service.name}${powerApiSuffix}HandlerFactory.java"
    b.setName(serverPath)
    logger.info(s"Generating Akka gRPC service handler for ${service.packageName}.${service.name}")
    b.build
  }
}
