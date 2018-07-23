/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.sbt.test

import akka.grpc.gen.StdoutLogger
import akka.grpc.gen.scaladsl.play.{ PlayScalaClientCodeGenerator, PlayScalaServerCodeGenerator }
import akka.grpc.gen.scaladsl.{ ScalaBothCodeGenerator, ScalaMarshallersCodeGenerator }
import akka.grpc.sbt.AkkaGrpcPlugin.ProtocBridgeSbtPluginCodeGenerator

/**
 * An easy to use accessor to be used reflectively in the main sbt project,
 * only used in the ReflectiveCodeGen plugin in the main sbt project, which is needed for the interop-tests subproject
 */
class PlayScalaCompositeCodeGenerator {
  def instance(): protocbridge.ProtocCodeGenerator =
    new ProtocBridgeSbtPluginCodeGenerator(
      new CompositeCodeGenerator(Seq(
      ScalaBothCodeGenerator,
      ScalaMarshallersCodeGenerator,
      PlayScalaServerCodeGenerator,
      PlayScalaClientCodeGenerator)),
      StdoutLogger)
}
