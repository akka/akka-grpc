/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import akka.grpc.gen.Logger
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.ScalaServer.txt.PowerApiTrait

object ScalaPowerApiTraitCodeGenerator extends ScalaCodeGenerator {
  override def name = "akka-grpc-scaladsl-power-api-trait"

  override def perServiceContent = super.perServiceContent + generatePowerApiTrait

  val generatePowerApiTrait: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(PowerApiTrait(service).body)
    b.setName(s"${service.packageDir}/${service.name}PowerApi.scala")
    logger.info(s"Generating Akka gRPC service power API interface for ${service.packageName}.${service.name}")
    b.build
  }
}
