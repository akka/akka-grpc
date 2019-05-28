/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl.play

import scala.collection.immutable
import akka.grpc.gen.Logger
import akka.grpc.gen.javadsl.{ JavaCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayJavaServer.txt.Router
import templates.PlayJavaServer.txt.RouterUsingActions

case class PlayJavaServerCodeGenerator(usePlayActions: Boolean = false) extends JavaCodeGenerator {
  override def name: String = "akka-grpc-play-server-java"

  override def perServiceContent = super.perServiceContent ++ (
    if (usePlayActions) Set(generatePlainRouterUsingActions, generatePowerRouterUsingActions)
    else Set(generatePlainRouter, generatePowerRouter)
  )

  private val generatePlainRouter: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service, powerApis = false).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}Router.java")
    logger.info(s"Generating Akka gRPC service play router for ${service.packageName}.${service.name}")
    immutable.Seq(b.build)
  }

  private val generatePowerRouter: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    if (service.serverPowerApi) {
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(Router(service, powerApis = true).body)
      b.setName(s"${service.packageDir}/Abstract${service.name}PowerApiRouter.java")
      logger.info(s"Generating Akka gRPC service power API play router for ${service.packageName}.${service.name}")
      immutable.Seq(b.build)
    } else immutable.Seq.empty
  }

  private val generatePlainRouterUsingActions: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(RouterUsingActions(service, powerApis = false).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}Router.java")
    logger.info(s"Generating Akka gRPC service play router for ${service.packageName}.${service.name}")
    immutable.Seq(b.build)
  }

  private val generatePowerRouterUsingActions: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    if (service.serverPowerApi) {
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(RouterUsingActions(service, powerApis = true).body)
      b.setName(s"${service.packageDir}/AbstractPowerApiRouter.java")
      logger.info(s"Generating Akka gRPC service power API play router for ${service.packageName}.${service.name}")
      immutable.Seq(b.build)
    } else immutable.Seq.empty
  }
}
