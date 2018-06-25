/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.sbt.test

import akka.grpc.sbt.AkkaGrpcPlugin.ProtocBridgeSbtPluginCodeGenerator

/**
 * An easy to use accessor to be used reflectively in the main sbt project,
 * only used in the ReflectiveCodeGen plugin in the main sbt project, which is needed for the interop-tests subproject
 */
class AkkaCompositeCodeGenerator {
  def instance(): protocbridge.ProtocCodeGenerator =
    new ProtocBridgeSbtPluginCodeGenerator(CompositeCodeGenerator)
}
