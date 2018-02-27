package akka.grpc.sbt

import akka.http.grpc.ScalaServerCodeGenerator

/** An easy to use accessor to be used reflectively in the main sbt project */
class AkkaScalaServerGrpcCodeGenerator {
  def instance(): protocbridge.ProtocCodeGenerator =
    new ProtocBridgeSbtPluginCodeGenerator(new ScalaServerCodeGenerator)
}
