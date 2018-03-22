/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

import scala.collection.immutable

import scala.collection.JavaConverters._
import com.google.protobuf.Descriptors._
import com.trueaccord.scalapb.compiler.{ DescriptorPimps, GeneratorParams }

case class Service(packageName: String, name: String, grpcName: String, methods: immutable.Seq[Method]) {
  def serializers: Set[Serializer] = (methods.map(_.deserializer) ++ methods.map(_.serializer)).toSet
}

object Service {
  def apply(generatorParams: GeneratorParams, fileDesc: FileDescriptor, serviceDescriptor: ServiceDescriptor): Service = {
    implicit val ops = new DescriptorPimps() {
      override def params: GeneratorParams = generatorParams
    }
    import ops._

    val serviceClassName = serviceDescriptor.getName

    Service(
      fileDesc.scalaPackageName,
      serviceClassName,
      fileDesc.getPackage + "." + serviceDescriptor.getName,
      serviceDescriptor.getMethods.asScala.map(method ⇒ Method(method)).to[immutable.Seq])
  }
}
