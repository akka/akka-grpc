/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import akka.grpc.gen.Logger
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.ScalaServer.txt._

case class ScalaServerCodeGenerator(powerApis: Boolean = false) extends ScalaCodeGenerator {
  import ScalaServerCodeGenerator._
  override def name = "akka-grpc-scaladsl-server"

  override def perServiceContent =
    if (powerApis) super.perServiceContent + ScalaCodeGenerator.generateServiceFile + ScalaServerCodeGenerator.generatePowerService + generatePowerHandler
    else super.perServiceContent + ScalaCodeGenerator.generateServiceFile + generateHandler
}

object ScalaServerCodeGenerator {
  val generatePowerService: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(PowerApiTrait(service).body)
    b.setName(s"${service.packageDir}/${service.name}PowerApi.scala")
    logger.info(s"Generating Akka gRPC file ${b.getName}")
    //    logger.info(s"Generating Akka gRPC extended service interface ${service.packageName}.${service.name}Extended")
    b.build
  }

  val generateHandler: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service).body)
    b.setName(s"${service.packageDir}/${service.name}Handler.scala")
    b.build
  }

  val generatePowerHandler: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(PowerApiHandler(service).body)
    b.setName(s"${service.packageDir}/${service.name}Handler.scala")
    logger.info(s"Generating Akka gRPC file ${b.getName}")
    //    logger.info(s"Generating Akka gRPC extended server handler ${service.packageName}.${service.name}ExtendedHandler")
    b.build
  }
}
