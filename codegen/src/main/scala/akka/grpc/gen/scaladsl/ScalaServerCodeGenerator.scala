/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import akka.grpc.gen.Logger
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.ScalaServer.txt._

case class ScalaServerCodeGenerator(powerApis: Boolean = false) extends ScalaCodeGenerator {
  override def name = "akka-grpc-scaladsl-server"

  override def perServiceContent = super.perServiceContent + generateHandler(powerApis)

  def generateHandler(powerApis: Boolean): (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service, powerApis).body)
    b.setName(s"${service.packageDir}/${service.name}Handler.scala")
    logger.info(s"Generating Akka gRPC service handler for ${service.packageName}.${service.name}")
    b.build
  }
}
