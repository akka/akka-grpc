/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.JavaClient.txt.{ Client, ClientPowerApi }

trait JavaClientCodeGenerator extends JavaCodeGenerator {
  override def name = "akka-grpc-javadsl-client"

  override def perServiceContent: Set[(Logger, Service) ⇒ CodeGeneratorResponse.File] =
    super.perServiceContent +
      JavaCodeGenerator.generateServiceFile +
      generateInterface +
      generateRaw

  def generateInterface(logger: Logger, service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Client(service).body)
    val clientPath = s"${service.packageDir}/${service.name}Client.java"
    b.setName(clientPath)
    logger.info(s"Generating Akka gRPC Client [${service.packageName}.${service.name}]")
    b.build
  }

  def generateRaw(logger: Logger, service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ClientPowerApi(service).body)
    val clientPath = s"${service.packageDir}/${service.name}ClientPowerApi.java"
    b.setName(clientPath)
    logger.info(s"Generating Akka gRPC Lifted Client interface[${service.packageName}.${service.name}]")
    b.build
  }

  override val suggestedDependencies = (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) => Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix, BuildInfo.version),
    // TODO: remove grpc-stub dependency once we have a akka-http based client #193
    Artifact("io.grpc", "grpc-stub", scalapb.compiler.Version.grpcJavaVersion))
}

object JavaClientCodeGenerator extends JavaClientCodeGenerator
