/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl.play

import akka.grpc.gen.Logger
import akka.grpc.gen.javadsl.{ JavaCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayJavaServer.txt.Router
import templates.PlayJavaServer.txt.RouterUsingActions

case class PlayJavaServerCodeGenerator(powerApis: Boolean = false, usePlayActions: Boolean = false) extends JavaCodeGenerator {
  override def name: String = "akka-grpc-play-server-java"

  override def perServiceContent = super.perServiceContent ++ (
    if (powerApis && usePlayActions) Set(generateRouterUsingActions(), generateRouterUsingActions(powerApis))
    else if (powerApis) Set(generateRouter(), generateRouter(powerApis))
    else if (usePlayActions) Set(generateRouterUsingActions())
    else Set(generateRouter())
  )

  private def generateRouter(powerApis: Boolean = false): (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}${if (powerApis) "PowerApi" else ""}Router.java")
    logger.info(s"Generating Akka gRPC service${if (powerApis) " power API" else ""} play router for ${service.packageName}.${service.name}")
    b.build
  }

  private def generateRouterUsingActions(powerApis: Boolean = false): (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(RouterUsingActions(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}${if (powerApis) "PowerApi" else ""}Router.java")
    logger.info(s"Generating Akka gRPC service${if (powerApis) " power API" else ""} play router for ${service.packageName}.${service.name}")
    b.build
  }
}
