/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.sbt

import protocbridge.{ JvmGenerator, Target }
import sbt.{ GlobFilter, _ }
import Keys._
import akka.grpc.gen.scaladsl.{ ScalaBothCodeGenerator }
import akka.grpc.gen.CodeGenerator
import sbtprotoc.ProtocPlugin
import scalapb.ScalaPbCodeGenerator

object AkkaGrpcPlugin extends AutoPlugin {
  import sbtprotoc.ProtocPlugin.autoImport._

  // don't enable automatically, you might not want to run it on every subproject automatically
  override def trigger = noTrigger
  override def requires = ProtocPlugin

  trait Keys { _: autoImport.type =>
    val akkaGrpcCodeGeneratorSettings = settingKey[Seq[String]]("Settings to pass to the code generator")
    val akkaGrpcCodeGenerators = settingKey[Seq[GeneratorAndSettings]]("The configured source generator")
    val akkaGrpcModelGenerators = settingKey[Seq[Target]]("The configured source generator for model classes")
  }

  object autoImport extends Keys {
    final case class GeneratorAndSettings(generator: CodeGenerator, settings: Seq[String] = Nil)
  }
  import autoImport._

  override def projectSettings = defaultSettings ++ configSettings(Compile) ++ configSettings(Test)

  private def defaultSettings =
    Seq(
      akkaGrpcCodeGeneratorSettings := Seq("flat_package"),
      akkaGrpcCodeGenerators := GeneratorAndSettings(ScalaBothCodeGenerator, akkaGrpcCodeGeneratorSettings.value) :: Nil)

  def configSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      akkaGrpcModelGenerators := Seq[Target]((JvmGenerator("scala", ScalaPbCodeGenerator), akkaGrpcCodeGeneratorSettings.value) -> sourceManaged.value),

      unmanagedResourceDirectories ++= (resourceDirectories in PB.recompile).value,
      watchSources in Defaults.ConfigGlobal ++= (sources in PB.recompile).value,

      // configure the proto gen automatically by adding our codegen:
      PB.targets :=
        akkaGrpcModelGenerators.value ++
        akkaGrpcCodeGenerators.value.map(adaptAkkaGenerator(sourceManaged.value)),

      // include proto files extracted from the dependencies with "protobuf" configuration by default
      PB.protoSources += PB.externalIncludePath.value) ++
      inTask(PB.recompile)(
        Seq(
          includeFilter := GlobFilter("*.proto"),

          managedSourceDirectories := Nil,
          unmanagedSourceDirectories := Seq(sourceDirectory.value),
          sourceDirectories := unmanagedSourceDirectories.value ++ managedSourceDirectories.value,
          managedSources := Nil,
          unmanagedSources := { Defaults.collectFiles(unmanagedSourceDirectories, includeFilter, excludeFilter).value },
          sources := managedSources.value ++ unmanagedSources.value,

          managedResourceDirectories := Nil,
          unmanagedResourceDirectories := resourceDirectory.value +: PB.protoSources.value,
          resourceDirectories := unmanagedResourceDirectories.value ++ managedResourceDirectories.value,
          managedResources := Nil,
          unmanagedResources := { Defaults.collectFiles(unmanagedResourceDirectories, includeFilter, excludeFilter).value },
          resources := managedResources.value ++ unmanagedResources.value)))

  def adaptAkkaGenerator(targetPath: File)(generatorAndSettings: GeneratorAndSettings): Target = {
    val adapted = new ProtocBridgeSbtPluginCodeGenerator(generatorAndSettings.generator)
    val generator = JvmGenerator(generatorAndSettings.generator.name, adapted)
    (generator, generatorAndSettings.settings) -> targetPath
  }
}
