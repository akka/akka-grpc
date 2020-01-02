/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact

/**
 * Code generator trait that is not directly bound to scala-pb or protoc (other than the types).
 */
trait CodeGenerator {
  import CodeGenerator._

  /** Generator name; example: `akka-grpc-scala` */
  def name: String

  def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse

  /** Takes Scala binary version and returns suggested dependency Seq **/
  def suggestedDependencies: ScalaBinaryVersion => Seq[Artifact]

  final def run(request: Array[Byte], logger: Logger): Array[Byte] =
    run(CodeGeneratorRequest.parseFrom(request), logger: Logger).toByteArray
}

object CodeGenerator {

  /** Holds the prefix of a given Scala binary version **/
  case class ScalaBinaryVersion(prefix: String)
}
