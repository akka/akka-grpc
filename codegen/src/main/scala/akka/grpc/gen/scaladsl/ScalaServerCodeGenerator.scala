/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import akka.grpc.gen.Logger
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.ScalaServer.txt._

class ScalaServerCodeGenerator extends ScalaCodeGenerator {
  import ScalaServerCodeGenerator._
  override def name = "akka-grpc-scaladsl-server"

  override def perServiceContent =
    super.perServiceContent + ScalaCodeGenerator.generateServiceFile ++ Set(generatePowerService) + generateHandler
}

object ScalaServerCodeGenerator {
  val generatePowerService: (Logger, Service) => Option[CodeGeneratorResponse.File] = (logger, service) => {
    if (service.serverPowerApis) {
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(PowerApiTrait(service).body)
      b.setName(s"${service.packageDir}/${service.name}PowerApi.scala")
      logger.info(s"Generating Akka gRPC file ${b.getName}")
      Option(b.build)
    } else None
  }

  def generateHandler: (Logger, Service) => Option[CodeGeneratorResponse.File] = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service).body)
    b.setName(s"${service.packageDir}/${service.name}Handler.scala")
    Option(b.build)
  }
}
