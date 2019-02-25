/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

import akka.grpc.gen.Logger
import akka.grpc.gen.scaladsl.{ ScalaCodeGenerator, ScalaServerCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayScala.txt._

case class PlayScalaServerCodeGenerator(powerApis: Boolean = false) extends ScalaCodeGenerator {
  override def name: String = "akka-grpc-play-server-scala"

  override def perServiceContent = super.perServiceContent + generateRouter() ++ (
    if (powerApis) Set(generateRouter(powerApis))
    else Set.empty
  )

  private def generateRouter(powerApis: Boolean = false): (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}${if (powerApis) "PowerApi" else ""}Router.scala")
    logger.info(s"Generating Akka gRPC service${if (powerApis) " power API" else ""} play router for ${service.packageName}.${service.name}")
    b.build
  }
}
