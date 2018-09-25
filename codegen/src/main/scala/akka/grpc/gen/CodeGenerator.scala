/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import akka.grpc.gen.javadsl.Service
import akka.grpc.gen.scaladsl.Service
import akka.grpc.gen.scaladsl.play.PlayScalaClientCodeGenerator
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact

import scala.annotation.tailrec

/**
 * Code generator trait that is not directly bound to scala-pb or protoc (other than the types).
 */
trait CodeGenerator {

  /** Generator name; example: `akka-grpc-scala` */
  def name: String

  def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse

  def suggestedDependencies: Seq[Artifact]

  final def run(request: Array[Byte], logger: Logger): Array[Byte] =
    run(CodeGeneratorRequest.parseFrom(request), logger: Logger).toByteArray
}

private[gen] object CodeGenerator {

  /** Extract the longest common package prefix for a list of packages. */
  private[gen] def commonPackage(packages: Seq[String]): String =
    packages.reduce(commonPackage(_, _))

  /** Extract the longest common package prefix for two packages. */
  private[gen] def commonPackage(a: String, b: String): String = {
    if (a == b) a else {
      val aPackages = a.split('.')
      val bPackages = b.split('.')
      @tailrec
      def countIdenticalPackage(pos: Int): Int = {
        if (aPackages(pos) == bPackages(pos)) countIdenticalPackage(pos + 1)
        else pos
      }

      val prefixLength = countIdenticalPackage(0)
      if (prefixLength == 0) "" // no common, use root package
      else aPackages.take(prefixLength).mkString(".")
    }
  }

}