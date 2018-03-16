package akka

import sbt._
import Keys._
import sbtprotoc.ProtocPlugin
import ProtocPlugin.autoImport.PB
import protocbridge.{Artifact, Generator, JvmGenerator, Target}
import sbt.internal.inc.classpath.ClasspathUtilities

import scalapb.ScalaPbCodeGenerator

/** A plugin that allows to use a code generator compiled in one subproject to be used in a test project */
object ReflectiveCodeGen extends AutoPlugin {
  // In the 'real' plugin we enable the 'flat_package' option by default to get package names
  // that are more consistent between Scala and Java.
  // Because the interop tests generate both Scala and Java code, however, here we disable this
  // option to avoid name clashes in the generated classes:
  val codeGeneratorSettings = Seq.empty

  override def projectSettings: Seq[Def.Setting[_]] =
    inConfig(Compile)(Seq(
      PB.generate :=
        // almost the same as `Def.sequential` but will return the "middle" value, ie. the result of the generation
        // Defines three steps:
        //   1) dynamically load the current code generator and plug it in the mutable generator
        //   2) run the generator
        //   3) delete the generation cache because it doesn't know that the generator may change
        Def.taskDyn {
          val _ = setCodeGenerator.value
          Def.taskDyn {
            val generationResult = generateTaskFromProtocPlugin.value

            Def.task {
              // path is defined in ProtocPlugin.sourceGeneratorTask
              val file = (streams in PB.generate).value.cacheDirectory / s"protobuf_${scalaBinaryVersion.value}"
              IO.delete(file)

              generationResult
            }
          }
        }.value
    )) ++
    Seq(
      mutableGenerator in Compile := createMutableGenerator(),
      PB.targets in Compile := Seq(
        // Scala model classes:
        (JvmGenerator("scala", ScalaPbCodeGenerator), codeGeneratorSettings) -> (sourceManaged in Compile).value,
        // Java model classes:
        PB.gens.java -> (sourceManaged in Compile).value,
        // akka-grpc code:
        (mutableGenerator in Compile).value.target -> (sourceManaged in Compile).value,
      ),
      setCodeGenerator := loadAndSetGenerator(
        // the magic sauce: use the output classpath from the the sbt-plugin project and instantiate generator from there
        (fullClasspath in Compile in ProjectRef(file("."), "akka-grpc-sbt-plugin")).value,
        (mutableGenerator in Compile).value
      ),
      PB.recompile in Compile ~= (_ => true)
    )

  final case class MutableGeneratorAccess(setUnderlying: protocbridge.ProtocCodeGenerator => Unit, target: (Generator, Seq[String]))
  val setCodeGenerator = taskKey[Unit]("grpc-set-code-generator")
  val mutableGenerator = settingKey[MutableGeneratorAccess]("mutable Gens for test")

  def createMutableGenerator(): MutableGeneratorAccess = {
    class MutableProtocCodeGenerator extends protocbridge.ProtocCodeGenerator {
      private[this] var _underlying: protocbridge.ProtocCodeGenerator = _

      def run(request: Array[Byte]): Array[Byte] =
        if (_underlying ne null)
          try _underlying.run(request)
          finally { _underlying = null }
        else throw new IllegalStateException(s"Didn't set mutable generator")

      override def suggestedDependencies: Seq[Artifact] =
        if (_underlying ne null) _underlying.suggestedDependencies
        else Nil

      def setUnderlying(generator: protocbridge.ProtocCodeGenerator): Unit = _underlying = generator
    }

    val adapted = new MutableProtocCodeGenerator
    MutableGeneratorAccess(adapted.setUnderlying _, (JvmGenerator(s"mutable", adapted), codeGeneratorSettings))
  }

  def loadAndSetGenerator(classpath: Classpath, access: MutableGeneratorAccess): Unit = {
    val cp = classpath.map(_.data)
    // ensure to set right parent classloader, so that protocbridge.ProtocCodeGenerator etc are
    // compatible with what is already accessible from this sbt build
    val loader = ClasspathUtilities.toLoader(cp, classOf[protocbridge.ProtocCodeGenerator].getClassLoader)
    val instance = loader.loadClass("akka.grpc.sbt.CompositeCodeGenerator").newInstance()
    type WithInstance = {
      def instance(): protocbridge.ProtocCodeGenerator
    }
    val generator = instance.asInstanceOf[WithInstance].instance
    access.setUnderlying(generator)
  }

  def generateTaskFromProtocPlugin: Def.Initialize[Task[Seq[File]]] = {
    // lookup and return `PB.generate := ...` setting from ProtocPlugin
    ProtocPlugin.projectSettings.find(_.key.key == PB.generate.key).get.init.asInstanceOf[Def.Initialize[Task[Seq[File]]]]
  }
}
