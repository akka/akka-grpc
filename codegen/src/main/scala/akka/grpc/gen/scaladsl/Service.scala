/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.collection.immutable

import scala.collection.JavaConverters._
import com.google.protobuf.Descriptors._
import scalapb.compiler.{ DescriptorImplicits, GeneratorParams }

case class Service(
    descriptor: String,
    packageName: String,
    name: String,
    grpcName: String,
    methods: immutable.Seq[Method],
    serverPowerApi: Boolean,
    usePlayActions: Boolean,
    comment: Option[String] = None) {
  def serializers: Set[Serializer] = (methods.map(_.deserializer) ++ methods.map(_.serializer)).toSet
  def packageDir = packageName.replace('.', '/')
}

object Service {
  def apply(
      generatorParams: GeneratorParams,
      fileDesc: FileDescriptor,
      serviceDescriptor: ServiceDescriptor,
      serverPowerApi: Boolean,
      usePlayActions: Boolean): Service = {
    implicit val ops = new DescriptorImplicits(generatorParams, fileDesc.getDependencies.asScala :+ fileDesc)
    import ops._

    val serviceClassName = serviceDescriptor.getName

    Service(
      fileDesc.fileDescriptorObjectFullName + ".javaDescriptor",
      fileDesc.scalaPackageName,
      serviceClassName,
      (if (fileDesc.getPackage.isEmpty) "" else fileDesc.getPackage + ".") + serviceDescriptor.getName,
      serviceDescriptor.getMethods.asScala.map(method => Method(method)).to[immutable.Seq],
      serverPowerApi,
      usePlayActions,
      serviceDescriptor.comment)
  }
}
