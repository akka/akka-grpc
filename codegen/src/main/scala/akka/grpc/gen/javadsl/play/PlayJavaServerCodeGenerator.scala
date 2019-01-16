/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl.play

import akka.grpc.gen.Logger
import akka.grpc.gen.javadsl.{ JavaCodeGenerator, JavaServerCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayJavaServer.txt.Router

case class PlayJavaServerCodeGenerator(powerApis: Boolean = false) extends JavaCodeGenerator {
  import JavaServerCodeGenerator._

  override def name: String = "akka-grpc-play-server-java"

  override def perServiceContent = super.perServiceContent ++ {
    if (powerApis) Set(generatePowerService) else Set.empty
  } ++ Set(generateHandlerFactory(powerApis), generateRouter)

  private val generateRouter: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service, powerApis).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}Router.java")
    b.build
  }
}
