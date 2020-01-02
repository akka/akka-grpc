/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.collection.immutable
import akka.grpc.gen.Logger
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.ScalaServer.txt.{ Handler, PowerApiTrait }

class ScalaServerCodeGenerator extends ScalaCodeGenerator {
  override def name = "akka-grpc-scaladsl-server"

  override def perServiceContent =
    super.perServiceContent + generatePlainHandler + generatePowerHandler + generatePowerApiTrait

  val generatePlainHandler: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service, powerApis = false).body)
    b.setName(s"${service.packageDir}/${service.name}Handler.scala")
    logger.info(s"Generating Akka gRPC service handler for ${service.packageName}.${service.name}")
    immutable.Seq(b.build)
  }

  val generatePowerHandler: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    if (service.serverPowerApi) {
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(Handler(service, powerApis = true).body)
      b.setName(s"${service.packageDir}/${service.name}PowerApiHandler.scala")
      logger.info(s"Generating Akka gRPC service power API handler for ${service.packageName}.${service.name}")
      immutable.Seq(b.build)
    } else immutable.Seq.empty
  }

  val generatePowerApiTrait: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    if (service.serverPowerApi) {
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(PowerApiTrait(service).body)
      b.setName(s"${service.packageDir}/${service.name}PowerApi.scala")
      logger.info(s"Generating Akka gRPC service power API interface for ${service.packageName}.${service.name}")
      immutable.Seq(b.build)
    } else immutable.Seq.empty
  }
}
object ScalaServerCodeGenerator extends ScalaServerCodeGenerator
