package akka.grpc.gen.scaladsl.play

import akka.grpc.gen.scaladsl.{ ScalaCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayScalaServer.txt.Router

object PlayScalaServerCodeGenerator extends ScalaCodeGenerator {
  override def name: String = "akka-grpc-play-server-scala"

  override def perServiceContent = super.perServiceContent + generateRouter

  private val generateRouter: Service => CodeGeneratorResponse.File = service => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}Router.scala")
    b.build
  }
}
