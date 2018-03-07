package akka.grpc.sbt

import akka.http.grpc.JavaServerCodeGenerator

/** An easy to use accessor to be used reflectively in the main sbt project */
class AkkaJavaServerGrpcCodeGenerator {
  def instance(): protocbridge.ProtocCodeGenerator =
    new ProtocBridgeSbtPluginCodeGenerator(JavaServerCodeGenerator)
}
