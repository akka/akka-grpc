/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl.play

import akka.grpc.gen.Logger
import akka.grpc.gen.javadsl.{ JavaCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayJavaServer.txt.{ AkkaGrpcServiceModule, Router }

object PlayJavaServerCodeGenerator extends PlayJavaServerCodeGenerator

trait PlayJavaServerCodeGenerator extends JavaCodeGenerator {

  val ServiceModuleName = "AkkaGrpcServiceModule"

  override def name: String = "akka-grpc-play-server-java"

  override def perServiceContent: Set[(Logger, Service) ⇒ CodeGeneratorResponse.File] =
    super.perServiceContent + generateRouter

  private val generateRouter: (Logger, Service) => CodeGeneratorResponse.File = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Router(service).body)
    b.setName(s"${service.packageDir}/${service.name}Router.java")
    b.build
  }

  // FIXME: This code is duplicated for the Scala codegen too
  override def staticContent(logger: Logger, allServices: Seq[Service]): Set[CodeGeneratorResponse.File] = {
    if (allServices.nonEmpty) {
      val packageName = Service.commonPackage(allServices)
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(AkkaGrpcServiceModule(packageName, allServices).body)
      b.setName(s"${packageName.replace('.', '/')}/${ServiceModuleName}.java")
      val set = Set(b.build)

      logger.info(s"Generated '$packageName.$ServiceModuleName'. \n" +
        s"Add 'play.modules.enabled += $packageName.$ServiceModuleName' to your configuration to bind services. " +
        s"The following services will be bound: \n" +
        allServices.map { service =>
          val serviceClass = service.packageName + "." + service.name
          s""" * @Named("impl") $serviceClass -> ${serviceClass}Impl\n""" +
            s"""   - You will need to create the implementation class '${serviceClass}Impl'.\n""" +
            s"""   - To use a different implementation class, set 'akka.grpc.service."$serviceClass".class' to a new classname.\n""" +
            s"""   - To disable binding an implementation class, set configuration 'akka.grpc.service."$serviceClass".enabled = false'."""
        }.mkString("\n") + "\n" +
        "Add the following to your routes file to support all services:\n" +
        allServices.map { service =>
          val serviceClass = service.packageName + "." + service.name
          s"->  / ${serviceClass}Router"
        }.mkString("\n"))

      set
    } else Set.empty
  }
}
