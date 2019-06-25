/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import com.google.protobuf.Descriptors.{ FileDescriptor, ServiceDescriptor }
import scalapb.compiler.{ DescriptorImplicits, GeneratorParams }

import scala.collection.JavaConverters._
import scala.collection.immutable

final case class Service(
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
  def apply(fileDesc: FileDescriptor, serviceDescriptor: ServiceDescriptor, serverPowerApi: Boolean, usePlayActions: Boolean): Service = {

    val comment = {
      // Use ScalaPB's implicit classes to avoid replicating the logic for comment extraction
      // Note that this be problematic if/when ScalaPB uses scala-specific stuff to do that
      implicit val ops = new DescriptorImplicits(GeneratorParams(), fileDesc.getDependencies.asScala :+ fileDesc.getFile)
      import ops._
      serviceDescriptor.comment
    }

    Service(
      fileDesc.getOptions.getJavaPackage,
      serviceDescriptor.getName,
      fileDesc.getPackage + "." + serviceDescriptor.getName,
      serviceDescriptor.getMethods.asScala.map(method ⇒ Method(method)).to[immutable.Seq],
      serverPowerApi,
      usePlayActions,
      comment)
  }
}
