/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import akka.grpc.gen.Logger
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.ScalaServer.txt._

trait ScalaServerCodeGenerator extends ScalaCodeGenerator {
  override def name = "akka-grpc-scaladsl-server"

  override def perServiceContent = super.perServiceContent + ScalaCodeGenerator.generateServiceFile + generateHandler

  def generateHandler(logger: Logger, service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service).body)
    b.setName(s"${service.packageDir}/${service.name}Handler.scala")
    b.build
  }

}

object ScalaServerCodeGenerator extends ScalaServerCodeGenerator
