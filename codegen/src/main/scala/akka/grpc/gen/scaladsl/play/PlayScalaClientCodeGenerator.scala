/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

import akka.grpc.gen.scaladsl.{ ScalaCodeGenerator, Service }
import akka.grpc.gen.{ CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayScala.txt.{ AkkaGrpcClientModule, ClientProvider }

import scala.annotation.tailrec

object PlayScalaClientCodeGenerator extends PlayScalaClientCodeGenerator

trait PlayScalaClientCodeGenerator extends ScalaCodeGenerator {

  val ClientModuleName = "AkkaGrpcClientModule"

  override def name: String = "akka-grpc-play-client-scala"

  override def perServiceContent = super.perServiceContent + generateClientProvider

  private val generateClientProvider: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ClientProvider(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}ClientProvider.scala")
    b.build
  }

  override def staticContent(logger: Logger, allServices: Seq[Service]): Set[CodeGeneratorResponse.File] = {
    if (allServices.nonEmpty) {
      val packageName = Service.commonPackage(allServices)
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(AkkaGrpcClientModule(packageName, allServices).body)
      b.setName(s"${packageName.replace('.', '/')}/${ClientModuleName}.scala")
      val set = Set(b.build)
      logger.info(s"Generated [${packageName}.${ClientModuleName}] add it to play.modules.enabled and a section " +
        "with Akka gRPC client config under akka.grpc.client.\"servicepackage.ServiceName\" to be able to inject " +
        "client instances.")
      set
    } else Set.empty
  }

}
