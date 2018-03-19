package akka.grpc.sbt

import protocbridge.{ Generator, JvmGenerator, Target }
import sbt._
import Keys._
import akka.http.grpc.scaladsl.ScalaServerCodeGenerator
import sbtprotoc.ProtocPlugin

import scalapb.ScalaPbCodeGenerator

object AkkaGrpcPlugin extends AutoPlugin {
  import sbtprotoc.ProtocPlugin.autoImport._

  // don't enable automatically, you might not want to run it on every subproject automatically
  override def trigger = noTrigger
  override def requires = ProtocPlugin

  trait Keys { _: autoImport.type =>
    val codeGeneratorSettings = settingKey[Seq[String]]("Settings to pass to the code generator")
    val akkaGrpcCodeGenerators = settingKey[Seq[GeneratorAndSettings]]("The configured source generator")
    val akkaGrpcModelGenerators = settingKey[Seq[Target]]("The configured source generator for model classes")

  }
  object autoImport extends Keys {
    final case class GeneratorAndSettings(generator: akka.grpc.gen.CodeGenerator, settings: Seq[String] = Nil)
  }
  import autoImport._

  override def projectSettings = configSettings(Compile) ++ configSettings(Test)

  def configSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      codeGeneratorSettings := Seq("flat_package"),
      akkaGrpcCodeGenerators := GeneratorAndSettings(ScalaServerCodeGenerator, codeGeneratorSettings.value) :: Nil,
      akkaGrpcModelGenerators := Seq[Target]((JvmGenerator("scala", ScalaPbCodeGenerator), codeGeneratorSettings.value) -> sourceManaged.value),
      // we configure the proto gen automatically by adding our codegen:
      PB.targets :=
        akkaGrpcModelGenerators.value ++
        akkaGrpcCodeGenerators.value.map(adaptAkkaGenerator(sourceManaged.value))))

  def adaptAkkaGenerator(targetPath: File)(generatorAndSettings: GeneratorAndSettings): Target = {
    val adapted = new ProtocBridgeSbtPluginCodeGenerator(generatorAndSettings.generator)
    val generator = JvmGenerator(generatorAndSettings.generator.name, adapted)
    (generator, generatorAndSettings.settings) -> targetPath
  }
}
