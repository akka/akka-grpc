/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.sbt

import akka.grpc.gen.CodeGenerator.ScalaBinaryVersion
import akka.grpc.gen.scaladsl.{ ScalaClientCodeGenerator, ScalaServerCodeGenerator, ScalaTraitCodeGenerator }
import akka.grpc.gen.javadsl.{ JavaClientCodeGenerator, JavaInterfaceCodeGenerator, JavaServerCodeGenerator }
import akka.grpc.gen.{ BuildInfo, ProtocSettings, Logger => GenLogger }
import protocbridge.Generator
import sbt.Keys._
import sbt._
import sbtprotoc.ProtocPlugin
import scalapb.ScalaPbCodeGenerator

import language.implicitConversions

object AkkaGrpcPlugin extends AutoPlugin {
  import sbtprotoc.ProtocPlugin.autoImport._

  // don't enable automatically, you might not want to run it on every subproject automatically
  override def trigger = noTrigger
  override def requires = ProtocPlugin

  // hack because we cannot access sbt logger from streams unless inside taskKeys and
  // we need it in settingsKeys
  private val generatorLogger = new GenLogger {
    @volatile var logger: Logger = ConsoleLogger()
    def debug(text: String): Unit = logger.debug(text)
    def info(text: String): Unit = logger.info(text)
    def warn(text: String): Unit = logger.warn(text)
    def error(text: String): Unit = logger.error(text)
  }

  object GeneratorOption extends Enumeration {
    protected case class Val(setting: String) extends super.Val
    implicit def valueToGeneratorOptionVal(x: Value): Val = x.asInstanceOf[Val]

    val ServerPowerApis = Val("server_power_apis")
    val UsePlayActions = Val("use_play_actions")

    val settings: Set[String] = values.map(_.setting)
  }

  trait Keys { _: autoImport.type =>

    object AkkaGrpc {
      sealed trait GeneratedSource
      sealed trait GeneratedServer extends GeneratedSource
      sealed trait GeneratedClient extends GeneratedSource

      case object Client extends GeneratedClient
      case object Server extends GeneratedServer

      sealed trait Language
      case object Scala extends Language
      case object Java extends Language
    }

    val akkaGrpcGeneratedLanguages = settingKey[Seq[AkkaGrpc.Language]](
      "Which languages to generate service and model classes for (AkkaGrpc.Scala, AkkaGrpc.Java)")
    val akkaGrpcGeneratedSources = settingKey[Seq[AkkaGrpc.GeneratedSource]](
      "Which of the sources to generate in addition to the gRPC protobuf messages (AkkaGrpc.Server, AkkaGrpc.Client)")
    val akkaGrpcExtraGenerators =
      settingKey[Seq[akka.grpc.gen.CodeGenerator]]("Extra generators to evaluate. Empty by default")
    val akkaGrpcGenerators = settingKey[Seq[protocbridge.Generator]](
      "Generators to evaluate. Populated based on akkaGrpcGeneratedLanguages, akkaGrpcGeneratedSources and akkaGrpcExtraGenerators, but can be extended if needed")
    val akkaGrpcCodeGeneratorSettings = settingKey[Seq[String]](
      "Boolean settings to pass to the code generators, empty (all false) by default.\n" +
      "ScalaPB settings: " + ProtocSettings.scalapb.mkString(", ") + "\n" +
      "Java settings: " + ProtocSettings.protocJava.mkString(", ") + "\n" +
      "Akka gRPC settings: " + GeneratorOption.settings.mkString(", "))
  }

  object autoImport extends Keys
  import autoImport._

  override def projectSettings: Seq[sbt.Setting[_]] = defaultSettings ++ configSettings(Compile) ++ configSettings(Test)

  private def defaultSettings =
    Seq(
      akkaGrpcCodeGeneratorSettings := Seq("flat_package"),
      akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client, AkkaGrpc.Server),
      akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
      akkaGrpcExtraGenerators := Seq.empty,
      libraryDependencies ++= {
        if (akkaGrpcGeneratedLanguages.value.contains(AkkaGrpc.Scala))
          // Make scalapb.proto available for import in user-defined protos for file and package-level options
          Seq("com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf")
        else Seq()
      },
      Compile / PB.recompile := {
        // hack to get our (dirty) hands on a proper sbt logger before running the generators
        generatorLogger.logger = streams.value.log
        (Compile / PB.recompile).value
      },
      Test / PB.recompile := {
        // hack to get our (dirty) hands on a proper sbt logger before running the generators
        generatorLogger.logger = streams.value.log
        (Test / PB.recompile).value
      },
      PB.protocVersion := BuildInfo.googleProtobufVersion)

  def configSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(
      (if (config == Compile || config == Test) Seq() // already supported by sbt-protoc by default
       else sbtprotoc.ProtocPlugin.protobufConfigSettings) ++
      Seq(
        akkaGrpcCodeGeneratorSettings / target := crossTarget.value / "akka-grpc" / Defaults.nameForSrc(
          configuration.value.name),
        managedSourceDirectories += (akkaGrpcCodeGeneratorSettings / target).value,
        unmanagedResourceDirectories ++= (PB.recompile / resourceDirectories).value,
        Defaults.ConfigGlobal / watchSources ++= (PB.recompile / sources).value,
        akkaGrpcGenerators := {
          generatorsFor(
            akkaGrpcGeneratedSources.value,
            akkaGrpcGeneratedLanguages.value,
            ScalaBinaryVersion(scalaBinaryVersion.value),
            generatorLogger) ++ akkaGrpcExtraGenerators.value.map(g =>
            GeneratorBridge.plainGenerator(g, ScalaBinaryVersion(scalaBinaryVersion.value), generatorLogger))
        },
        // configure the proto gen automatically by adding our codegen:
        PB.targets ++=
          targetsFor(
            (akkaGrpcCodeGeneratorSettings / target).value,
            akkaGrpcCodeGeneratorSettings.value,
            akkaGrpcGenerators.value),
        PB.protoSources += sourceDirectory.value / "proto") ++
      inTask(PB.recompile)(Seq(
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
        unmanagedResources := {
          Defaults.collectFiles(unmanagedResourceDirectories, includeFilter, excludeFilter).value
        },
        resources := managedResources.value ++ unmanagedResources.value)))

  def targetsFor(
      targetPath: File,
      settings: Seq[String],
      generators: Seq[protocbridge.Generator]): Seq[protocbridge.Target] =
    generators.map { generator =>
      protocbridge.Target(
        generator,
        targetPath,
        generator match {
          case PB.gens.java =>
            settings.filter(ProtocSettings.protocJava.contains)
          case protocbridge.JvmGenerator("scala", ScalaPbCodeGenerator) | scalapb.gen.SandboxedGenerator =>
            settings.filter(ProtocSettings.scalapb.contains)
          case _ =>
            settings
        })
    }

  // creates a seq of generator and per generator settings
  def generatorsFor(
      stubs: Seq[AkkaGrpc.GeneratedSource],
      languages: Seq[AkkaGrpc.Language],
      scalaBinaryVersion: ScalaBinaryVersion,
      logger: GenLogger): Seq[protocbridge.Generator] = {
    import AkkaGrpc._
    def toGen(codeGenerator: akka.grpc.gen.CodeGenerator) =
      GeneratorBridge.sandboxedGenerator(codeGenerator, scalaBinaryVersion, logger)
    // these two are the model/message (protoc) generators
    def ScalaGenerator: protocbridge.Generator = scalapb.gen.SandboxedGenerator
    // we have a default flat_package, but that doesn't play with the java generator (it fails)
    def JavaGenerator: protocbridge.Generator = PB.gens.java

    lazy val scalaBaseGenerators: Seq[Generator] = Seq(ScalaGenerator, toGen(ScalaTraitCodeGenerator))
    lazy val javaBaseGenerators: Seq[Generator] = Seq(JavaGenerator, toGen(JavaInterfaceCodeGenerator))
    lazy val baseGenerators: Seq[Generator] = languages match {
      case Seq(Scala) => scalaBaseGenerators
      case Seq(Java)  => javaBaseGenerators
      case Seq(_, _)  => scalaBaseGenerators ++ javaBaseGenerators
    }

    val generators = (for {
      stub <- stubs
      language <- languages
    } yield (stub, language) match {
      case (Client, Scala) => ScalaClientCodeGenerator
      case (Server, Scala) => ScalaServerCodeGenerator
      case (Client, Java)  => JavaClientCodeGenerator
      case (Server, Java)  => JavaServerCodeGenerator
    }).distinct.map(toGen)

    if (generators.nonEmpty) baseGenerators ++ generators
    else generators
  }

  /** Sandbox a CodeGenerator, to prepare it to be added to akkaGrpcGenerators */
  def sandboxedGenerator(codeGenerator: akka.grpc.gen.CodeGenerator): Def.Initialize[protocbridge.Generator] =
    Def.setting {
      GeneratorBridge.sandboxedGenerator(codeGenerator, ScalaBinaryVersion(scalaBinaryVersion.value), generatorLogger)
    }

  /** Convert a CodeGenerator, to prepare it to be added to akkaGrpcGenerators without sandboxing */
  def plainGenerator(codeGenerator: akka.grpc.gen.CodeGenerator): Def.Initialize[protocbridge.Generator] =
    Def.setting {
      GeneratorBridge.plainGenerator(codeGenerator, ScalaBinaryVersion(scalaBinaryVersion.value), generatorLogger)
    }
}
