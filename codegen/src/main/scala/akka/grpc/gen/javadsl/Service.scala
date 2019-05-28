/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import com.google.protobuf.Descriptors.{ FileDescriptor, ServiceDescriptor }

import scala.collection.JavaConverters._
import scala.collection.immutable

final case class Service(packageName: String, name: String, grpcName: String, methods: immutable.Seq[Method], serverPowerApi: Boolean) {
  def serializers: Set[Serializer] = (methods.map(_.deserializer) ++ methods.map(_.serializer)).toSet
  def packageDir = packageName.replace('.', '/')
}

object Service {
  def apply(fileDesc: FileDescriptor, serviceDescriptor: ServiceDescriptor, serverPowerApi: Boolean): Service = {
    Service(
      fileDesc.getOptions.getJavaPackage,
      serviceDescriptor.getName,
      fileDesc.getPackage + "." + serviceDescriptor.getName,
      serviceDescriptor.getMethods.asScala.map(method ⇒ Method(method)).to[immutable.Seq],
      serverPowerApi)
  }
}
