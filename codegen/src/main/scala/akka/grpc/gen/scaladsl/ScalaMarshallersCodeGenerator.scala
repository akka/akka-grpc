/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.collection.immutable
import akka.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
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

  override def suggestedDependencies =
    (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) =>
      Artifact("com.typesafe.akka", s"akka-http_${scalaBinaryVersion.prefix}", BuildInfo.akkaHttpVersion) +: super
        .suggestedDependencies(scalaBinaryVersion)

  def generateMarshalling(logger: Logger, service: Service): immutable.Seq[CodeGeneratorResponse.File] = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Marshallers(service).body)
    b.setName(s"${service.packageDir}/${service.name}Marshallers.scala")
    immutable.Seq(b.build)
  }
}

object ScalaMarshallersCodeGenerator extends ScalaMarshallersCodeGenerator
