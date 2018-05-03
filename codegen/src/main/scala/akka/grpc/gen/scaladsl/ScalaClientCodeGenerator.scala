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

  override val staticContent = super.staticContent + generateGuavaConverters() + generateClientMarshaller()
  override def perServiceContent = super.perServiceContent + generateStub

  def generateStub(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Client(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}Client.scala")
    b.build
  }

  def generateGuavaConverters(): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    val packageName = "akka.grpc.internal"
    b.setContent(ChannelApiHelpers().body)
    b.setName(s"${packageName.replace('.', '/')}/ChannelApiHelpers.scala")
    b.build
  }

  def generateClientMarshaller(): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    val packageName = "akka.grpc.internal"
    b.setContent(Marshaller().body)
    b.setName(s"${packageName.replace('.', '/')}/Marshaller.scala")
    b.build
  }

  override val suggestedDependencies =
    // TODO: remove grpc-stub dependency once we have a akka-http based client #193
    Artifact("io.grpc", "grpc-stub", scalapb.compiler.Version.grpcJavaVersion) +: super.suggestedDependencies

  private def parseParameters(params: String): GeneratorParams = {
    params.split(",").map(_.trim).filter(_.nonEmpty).foldLeft[GeneratorParams](GeneratorParams()) {
      case (p, "java_conversions") => p.copy(javaConversions = true)
      case (p, "flat_package") => p.copy(flatPackage = true)
      case (p, "grpc") => p.copy(grpc = true)
      case (p, "single_line_to_string") => p.copy(singleLineToProtoString = true)
      case (x, _) => x
    }
  }
}

object ScalaClientCodeGenerator extends ScalaClientCodeGenerator