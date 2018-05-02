/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.sbt

import sbt.{ GlobFilter, _ }
import Keys._
import akka.grpc.gen.javadsl.{ JavaBothCodeGenerator, JavaClientCodeGenerator, JavaServerCodeGenerator }
import akka.grpc.gen.scaladsl.{ ScalaBothCodeGenerator, ScalaClientCodeGenerator, ScalaMarshallersCodeGenerator, ScalaServerCodeGenerator }
import akka.grpc.gen.CompositeCodeGenerator
import sbtprotoc.ProtocPlugin
import scalapb.ScalaPbCodeGenerator
import templates.ScalaCommon.txt.Marshallers

object AkkaGrpcPlugin extends AutoPlugin {
  import sbtprotoc.ProtocPlugin.autoImport._

  // don't enable automatically, you might not want to run it on every subproject automatically
  override def trigger = noTrigger
  override def requires = ProtocPlugin

  trait Keys { _: autoImport.type =>

    object AkkaGrpc {
      sealed trait TargetStub
      case object Client extends TargetStub
      case object Server extends TargetStub

      sealed trait TargetLanguage
      case object Scala extends TargetLanguage
      case object Java extends TargetLanguage
    }

    val akkaGrpcTargetLanguages = settingKey[Seq[AkkaGrpc.TargetLanguage]]("Which languages to generate service and model classes for (AkkaGrpc.Scala, AkkaGrpc.Java)")
    val akkaGrpcTargetStubs = settingKey[Seq[AkkaGrpc.TargetStub]]("Which of the stubs to generate (AkkaGrpc.Server, AkkaGrpc.Client)")
    val akkaGrpcCodeGeneratorSettings = settingKey[Seq[String]]("Settings to pass to the code generator")
  }

  object autoImport extends Keys
  import autoImport._

  override def projectSettings: Seq[sbt.Setting[_]] = defaultSettings ++ configSettings(Compile) ++ configSettings(Test)

  private def defaultSettings =
    Seq(
      akkaGrpcCodeGeneratorSettings := Seq("flat_package"),
      akkaGrpcTargetStubs := Seq(AkkaGrpc.Client, AkkaGrpc.Server),
      akkaGrpcTargetLanguages := Seq(AkkaGrpc.Scala),

      // configure the proto gen automatically by adding our codegen:
      PB.targets :=
        targetsFor(
          sourceManaged.value,
          akkaGrpcCodeGeneratorSettings.value,
          akkaGrpcTargetStubs.value, akkaGrpcTargetLanguages.value))

  def configSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(

      unmanagedResourceDirectories ++= (resourceDirectories in PB.recompile).value,
      watchSources in Defaults.ConfigGlobal ++= (sources in PB.recompile).value,

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

  private def targetsFor(targetPath: File, settings: Seq[String], stubs: Seq[AkkaGrpc.TargetStub], languages: Seq[AkkaGrpc.TargetLanguage]): Seq[protocbridge.Target] = {
    val generators = generatorsFor(stubs, languages)
    // TODO no way to provide per language + stub settings/target dirs - not sure if needed
    generators.map(generator => protocbridge.Target(generator, targetPath, settings))
  }

  private def generatorsFor(stubs: Seq[AkkaGrpc.TargetStub], languages: Seq[AkkaGrpc.TargetLanguage]): Seq[protocbridge.Generator] = {
    // these two are the model/message (protoc) generators
    def ScalaGenerator: protocbridge.Generator = protocbridge.JvmGenerator("scala", ScalaPbCodeGenerator)
    def JavaGenerator: protocbridge.Generator = PB.gens.java
    // this transforms the service client/server API generators to the right protocbridge type
    def toGenerator(codeGenerator: akka.grpc.gen.CodeGenerator): protocbridge.Generator = {
      val adapter = new ProtocBridgeSbtPluginCodeGenerator(codeGenerator)
      protocbridge.JvmGenerator(codeGenerator.name, adapter)
    }
    def Marshallers = toGenerator(ScalaMarshallersCodeGenerator)

    (stubs match {
      case Seq(_, _) =>
        languages match {
          case Seq(_, _) => Seq(ScalaGenerator, toGenerator(ScalaBothCodeGenerator), JavaGenerator, toGenerator(JavaBothCodeGenerator))
          case Seq(AkkaGrpc.Scala) => Seq(ScalaGenerator, toGenerator(ScalaBothCodeGenerator))
          case Seq(AkkaGrpc.Java) => Seq(PB.gens.java, toGenerator(JavaBothCodeGenerator))
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
    })
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

