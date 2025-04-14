/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import com.google.protobuf.Descriptors.{ FileDescriptor, ServiceDescriptor }
import protocgen.CodeGenRequest
import scalapb.compiler.{ DescriptorImplicits, GeneratorParams }

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.collection.immutable

final case class Service(
    descriptor: String,
    packageName: String,
    name: String,
    grpcName: String,
    methods: immutable.Seq[Method],
    serverPowerApi: Boolean,
    usePlayActions: Boolean,
    generateScalaHandlerFactory: Boolean,
    asyncReturnValues: Boolean,
    comment: Option[String] = None) {
  def serializers: Seq[Serializer] = (methods.map(_.deserializer) ++ methods.map(_.serializer)).distinct
  def packageDir = packageName.replace('.', '/')
}

object Service {
  def apply(
      request: CodeGenRequest,
      fileDesc: FileDescriptor,
      serviceDescriptor: ServiceDescriptor,
      serverPowerApi: Boolean,
      usePlayActions: Boolean,
      generateScalaHandlerFactory: Boolean,
      asyncReturnValues: Boolean): Service = {
    val comment = {
      // Use ScalaPB's implicit classes to avoid replicating the logic for comment extraction
      // Note that this be problematic if/when ScalaPB uses scala-specific stuff to do that
      val ops = DescriptorImplicits.fromCodeGenRequest(GeneratorParams(), request)
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
      serviceDescriptor.getMethods.asScala.toList.map(method => Method(request, method, asyncReturnValues)),
      serverPowerApi,
      usePlayActions,
      generateScalaHandlerFactory,
      asyncReturnValues,
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

  private[javadsl] def toCamelCase(name: String): String =
    if (name.isEmpty) ""
    else toCamelCaseRec(name, 0, new StringBuilder(name.length), true)

  @tailrec
  private def toCamelCaseRec(in: String, idx: Int, out: StringBuilder, capNext: Boolean): String = {
    if (idx >= in.length) out.toString
    else {
      val head = in.charAt(idx)
      if (head.isLetter)
        toCamelCaseRec(in, idx + 1, out.append(if (capNext) head.toUpper else head), false)
      else
        toCamelCaseRec(in, idx + 1, if (head.isDigit) out.append(head) else out, true)
    }
  }
}
