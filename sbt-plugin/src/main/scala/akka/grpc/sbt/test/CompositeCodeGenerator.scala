/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.sbt.test

import akka.grpc.gen.CodeGenerator
import akka.grpc.gen.javadsl.JavaBothCodeGenerator
import akka.grpc.gen.scaladsl.{ ScalaBothCodeGenerator, ScalaMarshallersCodeGenerator }
import com.google.protobuf.compiler.PluginProtos
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact

/**
 * Generate both Java and Scala server-side code, mainly for testing.
 */
object CompositeCodeGenerator extends CodeGenerator {
  override val name = "akka-composite"

  override def run(request: PluginProtos.CodeGeneratorRequest): PluginProtos.CodeGeneratorResponse = {
    val javaResult = JavaBothCodeGenerator.run(request)
    val scalaResult = ScalaBothCodeGenerator.run(request)
    val scalaMarshallersResult = ScalaMarshallersCodeGenerator.run(request)

    CodeGeneratorResponse.newBuilder()
      .addAllFile(javaResult.getFileList)
      .addAllFile(scalaResult.getFileList)
      .addAllFile(scalaMarshallersResult.getFileList)
      .build()
  }

  override def suggestedDependencies: Seq[Artifact] =
    JavaBothCodeGenerator.suggestedDependencies ++ ScalaBothCodeGenerator.suggestedDependencies
}
