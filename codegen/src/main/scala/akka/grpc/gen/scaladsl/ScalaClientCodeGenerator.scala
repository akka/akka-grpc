/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scalapb.compiler.GeneratorParams
import protocbridge.Artifact
import templates.ScalaClient.txt._

trait ScalaClientCodeGenerator extends ScalaCodeGenerator {
  override def name = "akka-grpc-scaladsl-client"

  override def perServiceContent = super.perServiceContent + ScalaCodeGenerator.generateServiceFile + generateStub

  def generateStub(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Client(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}Client.scala")
    b.build
  }

  override val suggestedDependencies =
    // TODO: remove grpc-stub dependency once we have a akka-http based client #193
    Artifact("io.grpc", "grpc-stub", scalapb.compiler.Version.grpcJavaVersion) +: super.suggestedDependencies
}

object ScalaClientCodeGenerator extends ScalaClientCodeGenerator