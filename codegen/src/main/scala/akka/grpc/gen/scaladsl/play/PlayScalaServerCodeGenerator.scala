/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

import scala.collection.immutable
import akka.grpc.gen.Logger
import akka.grpc.gen.scaladsl.{ ScalaCodeGenerator, ScalaServerCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayScala.txt._

class PlayScalaServerCodeGenerator extends ScalaCodeGenerator {

  override def name: String = "akka-grpc-play-server-scala"

  override def perServiceContent = super.perServiceContent + generatePlainRouter + generatePowerRouter

  private val generatePlainRouter: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()

    if (service.usePlayActions) b.setContent(RouterUsingActions(service, powerApis = false).body)
    else b.setContent(Router(service, powerApis = false).body)

    b.setName(s"${service.packageDir}/AbstractRouter.scala")
    logger.info(s"Generating Akka gRPC service play router for ${service.packageName}.${service.name}")
    immutable.Seq(b.build)
  }

  private val generatePowerRouter: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    if (service.serverPowerApi) {
      val b = CodeGeneratorResponse.File.newBuilder()

      if (service.usePlayActions) b.setContent(RouterUsingActions(service, powerApis = true).body)
      else b.setContent(Router(service, powerApis = true).body)

      b.setName(s"${service.packageDir}/Abstract${service.name}PowerApiRouter.scala")
      logger.info(s"Generating Akka gRPC service power API play router for ${service.packageName}.${service.name}")
      immutable.Seq(b.build)
    } else immutable.Seq.empty
  }
}
object PlayScalaServerCodeGenerator extends PlayScalaServerCodeGenerator
