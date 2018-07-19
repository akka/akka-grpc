/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.{ BuildInfo, Logger }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.JavaClient.txt.Client

trait JavaClientCodeGenerator extends JavaCodeGenerator {
  override def name = "akka-grpc-javadsl-client"

  override def perServiceContent: Set[(Logger, Service) ⇒ CodeGeneratorResponse.File] = super.perServiceContent +
    JavaCodeGenerator.generateServiceFile + generateStub

  override val staticContent = super.staticContent

  def generateStub(logger: Logger, service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Client(service).body)
    val clientPath = s"${service.packageDir}/${service.name}Client.java"
    b.setName(clientPath)
    logger.info(s"Generating Akka gRPC client for ${service.name} in $clientPath")
    b.build
  }

  override val suggestedDependencies = Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version),
    // TODO: remove grpc-stub dependency once we have a akka-http based client #193
    Artifact("io.grpc", "grpc-stub", scalapb.compiler.Version.grpcJavaVersion))
}

object JavaClientCodeGenerator extends JavaClientCodeGenerator
