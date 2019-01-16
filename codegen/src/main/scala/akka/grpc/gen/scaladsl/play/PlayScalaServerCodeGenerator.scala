/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

import akka.grpc.gen.Logger
import akka.grpc.gen.scaladsl.{ ScalaCodeGenerator, ScalaServerCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayScala.txt._

case class PlayScalaServerCodeGenerator(powerApis: Boolean = false) extends ScalaCodeGenerator {

  import ScalaServerCodeGenerator._

  override def name: String = "akka-grpc-play-server-scala"

  override def perServiceContent = super.perServiceContent ++ {
    if (powerApis) Set(generatePowerService) else Set.empty
  } ++ Set(generateHandler(powerApis), generateRouter)

  private val generateRouter: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}Router.scala")
    logger.info(s"Generating Akka gRPC file ${b.getName}")
    b.build
  }
}
