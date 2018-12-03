/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

import akka.grpc.gen.Logger
import akka.grpc.gen.scaladsl.{ ScalaCodeGenerator, ScalaServerCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayScala.txt._

case class PlayScalaServerCodeGenerator(powerApis: Boolean = false, usePlayActions: Boolean = false) extends ScalaCodeGenerator {

  import ScalaServerCodeGenerator._

  override def name: String = "akka-grpc-play-server-scala"

  override def perServiceContent =
    super.perServiceContent ++ ((powerApis, usePlayActions) match {
      case (true, true) => Set(generatePowerService, generateHandler(powerApis), generateRouterUsingActions)
      case (false, true) => Set(generateHandler(powerApis), generateRouterUsingActions)
      case (true, false) => Set(generatePowerService, generateHandler(powerApis), generateRouter)
      case (false, false) => Set(generateHandler(powerApis), generateRouter)
    })

  private val generateRouter: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}Router.scala")
    logger.info(s"Generating Akka gRPC file ${b.getName}")
    b.build
  }

  private val generateRouterUsingActions: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(RouterUsingActions(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}Router.scala")
    logger.info(s"Generating Akka gRPC file ${b.getName}")
    b.build
  }
}
