/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import akka.grpc.gen.{ BuildInfo, Logger }
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

  override def suggestedDependencies = (scalaBinaryVersion: String) =>
    Artifact("com.typesafe.akka", s"akka-http_$scalaBinaryVersion", BuildInfo.akkaHttpVersion) +: super.suggestedDependencies(scalaBinaryVersion)

  def generateMarshalling(logger: Logger, service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Marshallers(service).body)
    b.setName(s"${service.packageDir}/${service.name}Marshallers.scala")
    b.build
  }
}

object ScalaMarshallersCodeGenerator extends ScalaMarshallersCodeGenerator