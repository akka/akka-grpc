/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl.play

import akka.grpc.gen.javadsl.{ JavaCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayJavaServer.txt.Router

object PlayJavaServerCodeGenerator extends JavaCodeGenerator {
  override def name: String = "akka-grpc-play-server-java"

  override def perServiceContent: Set[Service ⇒ CodeGeneratorResponse.File] =
    super.perServiceContent + generateRouter

  private val generateRouter: Service => CodeGeneratorResponse.File = service => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service).body)
    b.setName(s"${service.packageDir}/Abstract${service.name}Router.java")
    b.build
  }

}
