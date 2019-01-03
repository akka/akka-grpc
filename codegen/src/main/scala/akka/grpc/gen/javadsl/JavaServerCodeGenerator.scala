/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.JavaServer.txt.Handler

trait JavaServerCodeGenerator extends JavaCodeGenerator {
  override def name = "akka-grpc-javadsl-server"

  override def perServiceContent: Set[(Logger, Service) ⇒ CodeGeneratorResponse.File] = super.perServiceContent +
    JavaCodeGenerator.generateServiceFile + generateHandlerFactory

  def generateHandlerFactory(logger: Logger, service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service).body)

    val serverPath = s"${service.packageDir}/${service.name}HandlerFactory.java"
    b.setName(serverPath)
    b.build
  }

  override val suggestedDependencies = (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) => Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix, BuildInfo.version))
}

object JavaServerCodeGenerator extends JavaServerCodeGenerator
