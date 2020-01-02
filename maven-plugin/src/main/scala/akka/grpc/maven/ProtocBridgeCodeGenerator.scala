/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.maven

import akka.grpc.gen.Logger
import protocbridge.Artifact

/**
 * Adapts existing [[akka.grpc.gen.CodeGenerator]] into the protocbridge required type
 */
class ProtocBridgeCodeGenerator(
    impl: akka.grpc.gen.CodeGenerator,
    scalaBinaryVersion: akka.grpc.gen.CodeGenerator.ScalaBinaryVersion,
    logger: Logger)
    extends protocbridge.ProtocCodeGenerator {
  override def run(request: Array[Byte]): Array[Byte] = impl.run(request, logger)
  override def suggestedDependencies: Seq[Artifact] = impl.suggestedDependencies(scalaBinaryVersion)
  override def toString = s"ProtocBridgeSbtPluginCodeGenerator(${impl.name}: $impl)"
}
