/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.{BuildInfo, CodeGenerator, Logger}
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import protocbridge.Artifact
import templates.JavaCommon.txt.ApiInterface

import scala.collection.JavaConverters._

abstract class JavaCodeGenerator extends CodeGenerator {

  /** Override this to add generated files per service */
  def perServiceContent: Set[(Logger, Service) ⇒ CodeGeneratorResponse.File] = Set.empty

  /** Override these to add service-independent generated files */
  def staticContent(logger: Logger): Set[CodeGeneratorResponse.File] = Set.empty
  def staticContent(logger: Logger, allServices: Seq[Service]): Set[CodeGeneratorResponse.File] = Set.empty

  override def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder

    // generate services code here, the data types we want to leave to scalapb

    val fileDescByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
        case (acc, fp) =>
          val deps = fp.getDependencyList.asScala.map(acc).toArray
          acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps))
      }

    val services = (for {
      file ← request.getFileToGenerateList.asScala
      fileDesc = fileDescByName(file)
      serviceDesc ← fileDesc.getServices.asScala
    } yield Service(fileDesc, serviceDesc)).toVector


    for {
      service <- services
      generator ← perServiceContent
    } {
      b.addFile(generator(logger, service))
    }

    staticContent(logger).map(b.addFile)
    staticContent(logger, services).map(b.addFile)

    b.build()
  }

  def generateServiceInterface(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiInterface(service).body)
    b.setName(s"${service.packageDir}/${service.name}.java")
    b.build
  }

  override val suggestedDependencies = Seq(
    Artifact(BuildInfo.organization, BuildInfo.runtimeArtifactName, BuildInfo.version),
  )
}

object JavaCodeGenerator {
  val generateServiceFile: (Logger, Service) ⇒ CodeGeneratorResponse.File = (logger, service) ⇒ {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiInterface(service).body)
    b.setName(s"${service.packageDir}/${service.name}.java")
    logger.info(s"Generating Akka gRPC service interface for [${service.packageName}.${service.name}]")
    b.build
  }
}
