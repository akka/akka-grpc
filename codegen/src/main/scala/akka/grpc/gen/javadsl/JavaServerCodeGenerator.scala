/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.BuildInfo
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.JavaServer.txt.Handler

trait JavaServerCodeGenerator extends JavaCodeGenerator {
  override def name = "akka-grpc-javadsl-server"

  override def perServiceContent: Set[Service ⇒ CodeGeneratorResponse.File] = super.perServiceContent +
    JavaCodeGenerator.generateServiceFile + generateHandlerFactory

  def generateHandlerFactory(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service).body)
    b.setName(s"${service.packageDir}/${service.name}HandlerFactory.java")
    b.build
  }

  override val suggestedDependencies = Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version),
  )
}

object JavaServerCodeGenerator extends JavaServerCodeGenerator
