/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl.play

import akka.grpc.gen.Logger
import akka.grpc.gen.javadsl.{ JavaCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayJavaServer.txt.Router

object PlayJavaServerCodeGenerator extends PlayJavaServerCodeGenerator(powerApis = false)

case class PlayJavaServerCodeGenerator(powerApis: Boolean = false) extends JavaCodeGenerator {
  override def name: String = "akka-grpc-play-server-java"

  override def perServiceContent = super.perServiceContent + generateRouter() ++ (
    if (powerApis) Set(generateRouter(powerApis))
    else Set.empty
  )

  private def generateRouter(powerApis: Boolean = false): (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}${if (powerApis) "PowerApi" else ""}Router.java")
    logger.info(s"Generating Akka gRPC service${if (powerApis) " power API" else ""} play router for ${service.packageName}.${service.name}")
    b.build
  }
}
