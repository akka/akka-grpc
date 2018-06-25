/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.sbt

import sbt.{ GlobFilter, _ }
import Keys._
import akka.grpc.gen.javadsl.{ JavaBothCodeGenerator, JavaClientCodeGenerator, JavaServerCodeGenerator }
import akka.grpc.gen.scaladsl.{ ScalaBothCodeGenerator, ScalaClientCodeGenerator, ScalaMarshallersCodeGenerator, ScalaServerCodeGenerator }
import sbtprotoc.ProtocPlugin
import scalapb.ScalaPbCodeGenerator

object AkkaGrpcPlugin extends AutoPlugin {
  import sbtprotoc.ProtocPlugin.autoImport._

  // don't enable automatically, you might not want to run it on every subproject automatically
  override def trigger = noTrigger
  override def requires = ProtocPlugin

  trait Keys { _: autoImport.type =>

    object AkkaGrpc {
      sealed trait GeneratedSource
      case object Client extends GeneratedSource
      case object Server extends GeneratedSource

      sealed trait Language
      case object Scala extends Language
      case object Java extends Language
    }

    val akkaGrpcGeneratedLanguages = settingKey[Seq[AkkaGrpc.Language]](
      "Which languages to generate service and model classes for (AkkaGrpc.Scala, AkkaGrpc.Java)")
    val akkaGrpcGeneratedSources = settingKey[Seq[AkkaGrpc.GeneratedSource]](
      "Which of the sources to generate in addition to the gRPC protobuf messages (AkkaGrpc.Server, AkkaGrpc.Client)")
    val akkaGrpcExtraGenerators = settingKey[Seq[akka.grpc.gen.CodeGenerator]]("Extra generators to evaluate. Empty by default")
    val akkaGrpcGenerators = settingKey[Seq[protocbridge.Generator]]("Generators to evaluate. Populated based on akkaGrpcGeneratedLanguages, akkaGrpcGeneratedSources and akkaGrpcExtraGenerators, but can be extended if needed")
    val akkaGrpcCodeGeneratorSettings = settingKey[Seq[String]]("Settings to pass to the code generators")
  }

  object autoImport extends Keys
  import autoImport._

  override def projectSettings: Seq[sbt.Setting[_]] = defaultSettings ++ configSettings(Compile) ++ configSettings(Test)

  private def defaultSettings =
    Seq(
      akkaGrpcCodeGeneratorSettings := Seq("flat_package"),
      akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client, AkkaGrpc.Server),
      akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
      akkaGrpcExtraGenerators := Seq.empty)

  def configSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      unmanagedResourceDirectories ++= (resourceDirectories in PB.recompile).value,
      watchSources in Defaults.ConfigGlobal ++= (sources in PB.recompile).value,
      akkaGrpcGenerators := generatorsFor(akkaGrpcGeneratedSources.value, akkaGrpcGeneratedLanguages.value) ++ akkaGrpcExtraGenerators.value.map(toGenerator),

      // configure the proto gen automatically by adding our codegen:
      // FIXME: actually specifying separate Compile and Test target stub and languages does not work #194
      PB.targets :=
        targetsFor(
          sourceManaged.value,
          akkaGrpcCodeGeneratorSettings.value,
          akkaGrpcGenerators.value),

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

  private def targetsFor(targetPath: File, settings: Seq[String], generators: Seq[protocbridge.Generator]): Seq[protocbridge.Target] = {
    generators.map { generator =>
      protocbridge.Target(generator, targetPath, generator match {
        case PB.gens.java => settings.filterNot(_ == "flat_package")
        case _ => settings
      })
    }
  }

  // creates a seq of generator and per generator settings
  private def generatorsFor(stubs: Seq[AkkaGrpc.GeneratedSource], languages: Seq[AkkaGrpc.Language]): Seq[protocbridge.Generator] = {
    // these two are the model/message (protoc) generators
    def ScalaGenerator: protocbridge.Generator = protocbridge.JvmGenerator("scala", ScalaPbCodeGenerator)
    // we have a default flat_package, but that doesn't play with the java generator (it fails)
    def JavaGenerator: protocbridge.Generator = PB.gens.java

    stubs match {
      case Seq(_, _) =>
        languages match {
          case Seq(_, _) => Seq(ScalaGenerator, toGenerator(ScalaBothCodeGenerator), JavaGenerator, toGenerator(JavaBothCodeGenerator))
          case Seq(AkkaGrpc.Scala) => Seq(ScalaGenerator, toGenerator(ScalaBothCodeGenerator))
          case Seq(AkkaGrpc.Java) => Seq(JavaGenerator, toGenerator(JavaBothCodeGenerator))
        }
      case Seq(AkkaGrpc.Client) =>
        languages match {
          case Seq(_, _) => Seq(ScalaGenerator, toGenerator(ScalaClientCodeGenerator), JavaGenerator, toGenerator(JavaClientCodeGenerator))
          case Seq(AkkaGrpc.Scala) => Seq(ScalaGenerator, toGenerator(ScalaClientCodeGenerator))
          case Seq(AkkaGrpc.Java) => Seq(JavaGenerator, toGenerator(JavaClientCodeGenerator))
        }
      case Seq(AkkaGrpc.Server) =>
        languages match {
          case Seq(_, _) => Seq(ScalaGenerator, toGenerator(ScalaServerCodeGenerator), JavaGenerator, toGenerator(JavaServerCodeGenerator))
          case Seq(AkkaGrpc.Scala) => Seq(ScalaGenerator, toGenerator(ScalaServerCodeGenerator))
          case Seq(AkkaGrpc.Java) => Seq(JavaGenerator, toGenerator(JavaServerCodeGenerator))
        }
    }
  }

  // this transforms the Akka gRPC API generators to the right protocbridge type
  def toGenerator(codeGenerator: akka.grpc.gen.CodeGenerator): protocbridge.Generator = {
    val adapter = new ProtocBridgeSbtPluginCodeGenerator(codeGenerator)
    protocbridge.JvmGenerator(codeGenerator.name, adapter)
  }

  /**
   * Adapts existing [[akka.grpc.gen.CodeGenerator]] into the protocbridge required type
   */
  private[akka] class ProtocBridgeSbtPluginCodeGenerator(impl: akka.grpc.gen.CodeGenerator) extends protocbridge.ProtocCodeGenerator {
    override def run(request: Array[Byte]): Array[Byte] = impl.run(request)
    override def suggestedDependencies: Seq[protocbridge.Artifact] = impl.suggestedDependencies
    override def toString = s"ProtocBridgeSbtPluginCodeGenerator(${impl.name}: $impl)"
  }
}

