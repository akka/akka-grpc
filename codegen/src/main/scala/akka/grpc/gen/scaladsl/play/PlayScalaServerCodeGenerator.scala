/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

import akka.grpc.gen.Logger
import akka.grpc.gen.scaladsl.{ ScalaCodeGenerator, ScalaServerCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayScala.txt._

case class PlayScalaServerCodeGenerator(powerApis: Boolean = false, usePlayActions: Boolean = false) extends ScalaCodeGenerator {

  override def name: String = "akka-grpc-play-server-scala"

  override def perServiceContent = super.perServiceContent ++ ((powerApis, usePlayActions) match {
      case (true, true) => Set(generateRouterUsingActions(), generateRouterUsingActions(true))
      case (false, true) => Set(generateRouterUsingActions())
      case (true, false) => Set(generateRouter(), generateRouter(true))
      case (false, false) => Set(generateRouter())
    })

  private def generateRouter(powerApis: Boolean = false): (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}${if (powerApis) "PowerApi" else ""}Router.scala")
    logger.info(s"Generating Akka gRPC service${if (powerApis) " power API" else ""} play router for ${service.packageName}.${service.name}")
    b.build
  }

  private def generateRouterUsingActions(powerApis: Boolean = false): (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(RouterUsingActions(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}Router.scala")
    logger.info(s"Generating Akka gRPC file ${b.getName}")
    b.build
  }
}
