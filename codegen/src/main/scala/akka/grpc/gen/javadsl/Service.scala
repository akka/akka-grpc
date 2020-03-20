/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import com.google.protobuf.Descriptors.{ FileDescriptor, ServiceDescriptor }
import scalapb.compiler.{ DescriptorImplicits, GeneratorParams }

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.immutable

final case class Service(
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
      fileDesc: FileDescriptor,
      serviceDescriptor: ServiceDescriptor,
      serverPowerApi: Boolean,
      usePlayActions: Boolean): Service = {
    val comment = {
      // Use ScalaPB's implicit classes to avoid replicating the logic for comment extraction
      // Note that this be problematic if/when ScalaPB uses scala-specific stuff to do that
      implicit val ops =
        new DescriptorImplicits(GeneratorParams(), fileDesc.getDependencies.asScala :+ fileDesc.getFile)
      import ops._
      serviceDescriptor.comment
    }
    val packageName =
      if (fileDesc.getOptions.hasJavaPackage) fileDesc.getOptions.getJavaPackage
      else fileDesc.getPackage
    Service(
      outerClass(fileDesc) + ".getDescriptor()",
      packageName,
      serviceDescriptor.getName,
      (if (fileDesc.getPackage.isEmpty) "" else fileDesc.getPackage + ".") + serviceDescriptor.getName,
      serviceDescriptor.getMethods.asScala.map(method => Method(method)).to[immutable.Seq],
      serverPowerApi,
      usePlayActions,
      comment)
  }

  private[javadsl] def basename(name: String): String =
    name.replaceAll("^.*/", "").replaceAll("\\.[^\\.]*$", "")

  private[javadsl] def outerClass(t: FileDescriptor) =
    if (t.toProto.getOptions.hasJavaOuterClassname) t.toProto.getOptions.getJavaOuterClassname
    else {
      val className = Service.toCamelCase(protoName(t))
      if (hasConflictingClassName(t, className)) className + "OuterClass"
      else className
    }

  private def hasConflictingClassName(d: FileDescriptor, className: String): Boolean =
    d.findServiceByName(className) != null ||
    d.findMessageTypeByName(className) != null ||
    d.findEnumTypeByName(className) != null

  private[javadsl] def protoName(t: FileDescriptor) =
    t.getName.replaceAll("\\.proto", "").split("/").last

  private[javadsl] def toCamelCase(name: String): String = {
    if (name.isEmpty) ""
    else toCamelCaseRec(name.tail, new StringBuffer(name.head.toUpper.toString), false)
  }

  @tailrec
  private def toCamelCaseRec(in: String, out: StringBuffer, capNext: Boolean): String = {
    if (in.isEmpty) out.toString
    else {
      val head = in.head
      if (head.isLower) {
        if (capNext) toCamelCaseRec(in.tail, out.append(head.toUpper), false)
        else toCamelCaseRec(in.tail, out.append(head), false)
      } else if (head.isUpper) toCamelCaseRec(in.tail, out.append(head), false)
      else if (head.isDigit) toCamelCaseRec(in.tail, out.append(head), true)
      else toCamelCaseRec(in.tail, out, true)
    }
  }
}
