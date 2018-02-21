package akka.grpc.sbt

import sbtprotoc._
import akka.http.grpc.ScalaServerCodeGenerator
import protocbridge.JvmGenerator
import sbt._
import sbt.Keys._

import scala.collection.immutable

object AkkaGrpcSbtPlugin extends AutoPlugin {
  import sbtprotoc.ProtocPlugin.autoImport._

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  object Keys {
    val AkkaGrpc = config("akka-grpc") extend Compile

    // these are on purpose not just sbt source generators, we plug them into the existing infrastructure of sbt-protoc
    val akkaGrpcCodeGenerators = settingKey[akka.grpc.gen.CodeGenerator]("The configured source generator")
    val jvmCodeGenerator = settingKey[(JvmGenerator, Seq[String])]("Added as source gen to sbt-protoc")
  }
  import Keys._

  val autoImport = Keys

  override def projectConfigurations = Seq(AkkaGrpc)

  override def projectSettings = inConfig(AkkaGrpc)(Defaults.compileSettings ++ Seq(

    // depending on settings enable various source gens:

    akkaGrpcCodeGenerators := {
      new ScalaServerCodeGenerator
    },

    jvmCodeGenerator := {
      val impl = akkaGrpcCodeGenerators.value
      val adapted = new ProtocBridgeSbtPluginCodeGenerator(impl)

      println(s"Akka GRPC code gen: ${impl.name}")

      val generator = JvmGenerator(impl.name, adapted)

      // TODO we can put our own settings here
      val settings: Seq[String] = Seq(
        "flat_package" -> false,
        "java_conversions" -> false,
        "single_line_to_string" -> false).collect { case (settingName, v) if v => settingName }

      (generator, settings)
    },

    // we configure the proto gen automatically by adding our codegen:
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = false) -> (sourceManaged in Compile).value,
      jvmCodeGenerator.value -> (sourceManaged in Compile).value)))

}
