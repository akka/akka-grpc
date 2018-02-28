package akka.grpc.sbt

import akka.http.grpc.ScalaServerCodeGenerator
import protocbridge.JvmGenerator
import protocbridge.Target
import sbt._
import Keys._
import sbtprotoc.ProtocPlugin

object AkkaGrpcPlugin extends AutoPlugin {
  import sbtprotoc.ProtocPlugin.autoImport._

  // don't enable automatically, you might not want to run it on every subproject automatically
  override def trigger = noTrigger
  override def requires = ProtocPlugin

  trait Keys { _: autoImport.type =>
    // these are on purpose not just sbt source generators, we plug them into the existing infrastructure of sbt-protoc
    val akkaGrpcCodeGenerators = settingKey[Seq[GeneratorAndSettings]]("The configured source generator")
  }
  object autoImport extends Keys {
    case class GeneratorAndSettings(generator: akka.grpc.gen.CodeGenerator, settings: Seq[String] = Nil)
  }
  import autoImport._

  override def projectSettings = configSettings(Compile) ++ configSettings(Test)

  def configSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      akkaGrpcCodeGenerators := GeneratorAndSettings(ScalaServerCodeGenerator) :: Nil,

      // we configure the proto gen automatically by adding our codegen:
      PB.targets :=
        Seq[Target](scalapb.gen(grpc = false) -> sourceManaged.value) ++
        akkaGrpcCodeGenerators.value.map(adaptAkkaGenerator(sourceManaged.value))))

  def adaptAkkaGenerator(targetPath: File)(generatorAndSettings: GeneratorAndSettings): Target = {
    val adapted = new ProtocBridgeSbtPluginCodeGenerator(generatorAndSettings.generator)
    val generator = JvmGenerator(generatorAndSettings.generator.name, adapted)
    (generator, generatorAndSettings.settings) -> targetPath
  }
}
