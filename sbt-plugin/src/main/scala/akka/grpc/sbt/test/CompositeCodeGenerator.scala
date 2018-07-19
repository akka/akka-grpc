/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.sbt.test

import akka.grpc.gen.{ CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact

/**
 * Generate both Java and Scala server-side code, mainly for testing.
 */
class CompositeCodeGenerator(generators: Seq[CodeGenerator]) extends CodeGenerator {
  override val name = "akka-composite"

  override def run(request: PluginProtos.CodeGeneratorRequest, logger: Logger): PluginProtos.CodeGeneratorResponse = {
    generators.foldLeft(CodeGeneratorResponse.newBuilder())((builder, generator) => builder.addAllFile(generator.run(request, logger).getFileList)).build()
  }

  override def suggestedDependencies: Seq[Artifact] =
    generators.flatMap(_.suggestedDependencies)
}
