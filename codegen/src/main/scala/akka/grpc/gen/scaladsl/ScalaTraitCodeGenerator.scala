/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.collection.immutable
import akka.grpc.gen.Logger
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.ScalaCommon.txt.ApiTrait

object ScalaTraitCodeGenerator extends ScalaCodeGenerator {
  override def name = "akka-grpc-scaladsl-trait"

  override def perServiceContent = super.perServiceContent + generateServiceFile

  val generateServiceFile: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiTrait(service).body)
    b.setName(s"${service.packageDir}/${service.name}.scala")
    logger.info(s"Generating Akka gRPC service interface for ${service.packageName}.${service.name}")
    immutable.Seq(b.build)
  }
}
