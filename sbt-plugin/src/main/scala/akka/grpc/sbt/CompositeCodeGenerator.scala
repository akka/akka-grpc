package akka.grpc.sbt

import akka.http.grpc.CompositeCodeGenerator

/** An easy to use accessor to be used reflectively in the main sbt project */
class CompositeCodeGenerator {
  def instance(): protocbridge.ProtocCodeGenerator =
    new ProtocBridgeSbtPluginCodeGenerator(CompositeCodeGenerator)
}
