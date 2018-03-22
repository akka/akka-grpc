package akka.grpc.gen.scaladsl

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.ScalaCommon.txt._

/**
 * Has to be a separate generator rather than a parameter to the existing ones, because
 * it introduces a suggestedDependency on akka-http.
 */
trait ScalaMarshallersCodeGenerator extends ScalaCodeGenerator {
  def name = "akka-grpc-scaladsl-server-marshallers"

  override def perServiceContent = Set(generateMarshalling)

  override def suggestedDependencies: Seq[Artifact] =
    // TODO put the akka-http version in BuildInfo
    Artifact("com.typesafe.akka", "akka-http", "1.10.0") +: super.suggestedDependencies

  def generateMarshalling(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Marshallers(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}Marshallers.scala")
    b.build
  }
}

object ScalaMarshallersCodeGenerator extends ScalaMarshallersCodeGenerator