/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.Logger
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.JavaServer.txt.PowerApiInterface

object JavaPowerApiInterfaceCodeGenerator extends JavaCodeGenerator {
  override def name = "akka-grpc-javadsl-power-api-interface"

  override def perServiceContent: Set[(Logger, Service) ⇒ CodeGeneratorResponse.File] = super.perServiceContent + generatePowerService

  val generatePowerService: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(PowerApiInterface(service).body)
    b.setName(s"${service.packageDir}/${service.name}PowerApi.java")
    logger.info(s"Generating Akka gRPC service power interface for [${service.packageName}.${service.name}]")
    b.build
  }
}
