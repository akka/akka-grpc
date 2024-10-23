/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.collection.immutable
import scala.jdk.CollectionConverters._
import com.google.protobuf.Descriptors._
import protocgen.CodeGenRequest
import scalapb.compiler.{ DescriptorImplicits, GeneratorParams }

case class Service(
    descriptor: String,
    packageName: String,
    name: String,
    grpcName: String,
    methods: immutable.Seq[Method],
    serverPowerApi: Boolean,
    usePlayActions: Boolean,
    options: com.google.protobuf.DescriptorProtos.ServiceOptions,
    comment: Option[String] = None) {
  def serializers: Seq[Serializer] = (methods.map(_.deserializer) ++ methods.map(_.serializer)).distinct
  def packageDir = packageName.replace('.', '/')
}

object Service {
  def apply(
      request: CodeGenRequest,
      generatorParams: GeneratorParams,
      fileDesc: FileDescriptor,
      serviceDescriptor: ServiceDescriptor,
      serverPowerApi: Boolean,
      usePlayActions: Boolean): Service = {
    implicit val ops: DescriptorImplicits =
      DescriptorImplicits.fromCodeGenRequest(generatorParams, request)
    import ops._

    val serviceClassName = serviceDescriptor.getName

    Service(
      fileDesc.fileDescriptorObject.fullName + ".javaDescriptor",
      fileDesc.scalaPackage.fullName,
      serviceClassName,
      (if (fileDesc.getPackage.isEmpty) "" else fileDesc.getPackage + ".") + serviceDescriptor.getName,
      serviceDescriptor.getMethods.asScala.map(method => Method(method)).toList,
      serverPowerApi,
      usePlayActions,
      serviceDescriptor.getOptions,
      serviceDescriptor.comment)
  }
}
