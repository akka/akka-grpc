package akka.grpc.sbt

import sbt._
import sbt.Keys._

class AkkaGrpcSbt extends AutoPlugin {

  object Keys {
    val AkkaGrpc = config("akka-grpc") extend Compile
    val protocBin = settingKey[File]("location of protoc binary to invoke (must be 3.x+")
  }
  import Keys._

  val autoImport = Keys

  override def requires = plugins.JvmPlugin

  override def trigger = AllRequirements

  override def projectConfigurations = Seq(AkkaGrpc)

  override def projectSettings = inConfig(AkkaGrpc)(Defaults.compileSettings ++ Seq( // TODO automatically add protopb
  ))

}
