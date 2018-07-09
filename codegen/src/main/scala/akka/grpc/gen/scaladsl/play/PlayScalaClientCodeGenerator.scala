/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

import akka.grpc.gen.scaladsl.{ ScalaCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayScala.txt.{ ClientProvider, Module }

object PlayScalaClientCodeGenerator extends ScalaCodeGenerator {
  override def name: String = "akka-grpc-play-client-scala"

  override def perServiceContent = super.perServiceContent + generateClientProvider + generateModule

  private val generateClientProvider: Service => CodeGeneratorResponse.File = service => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ClientProvider(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}ClientProvider.scala")
    b.build
  }

  private val generateModule: Service => CodeGeneratorResponse.File = service => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Module(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}Module.scala")
    b.build
  }
}
