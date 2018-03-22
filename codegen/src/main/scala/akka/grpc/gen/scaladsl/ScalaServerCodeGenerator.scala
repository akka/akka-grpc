/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.ScalaServer.txt._

trait ScalaServerCodeGenerator extends ScalaCodeGenerator {
  def name = "akka-grpc-scaladsl-server"

  override def perServiceContent = super.perServiceContent + generateHandler

  def generateHandler(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Handler(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}Handler.scala")
    b.build
  }
}

object ScalaServerCodeGenerator extends ScalaServerCodeGenerator