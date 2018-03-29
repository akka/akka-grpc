/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.BuildInfo
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.JavaClient.txt.{Client, JavaChannelApiHelpers, JavaMarshaller}

trait JavaClientCodeGenerator extends JavaCodeGenerator {
  override def name = "akka-grpc-javadsl-client"

  override def perServiceContent = super.perServiceContent + generateStub
  override val staticContent = super.staticContent + generateFutureHelpers() + generateClientMarshaller()

  def generateStub(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Client(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}Client.java")
    b.build
  }

  def generateFutureHelpers(): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    val packageName = "akka.grpc.internal"
    b.setContent(JavaChannelApiHelpers().body)
    b.setName(s"${packageName.replace('.', '/')}/JavaChannelApiHelpers.scala")
    b.build
  }

  def generateClientMarshaller(): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    val packageName = "akka.grpc.internal"
    b.setContent(JavaMarshaller().body)
    b.setName(s"${packageName.replace('.', '/')}/JavaMarshaller.scala")
    b.build
  }

  override val suggestedDependencies = Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version),
  )
}

object JavaClientCodeGenerator extends JavaClientCodeGenerator
