package akka

import sbt._
import Keys._
import sbtprotoc.ProtocPlugin
import ProtocPlugin.autoImport.PB
import protocbridge.Artifact
import protocbridge.Generator
import protocbridge.JvmGenerator
import sbt.internal.inc.classpath.ClasspathUtilities

/** A plugin that allows to use a code generator compiled in one subproject to be used in a test project */
object ReflectiveCodeGen extends AutoPlugin {
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
      mutableGenerators in Compile := createMutableGenerators(),
      PB.targets in Compile := Seq(
        scalapb.gen(grpc = false) -> (sourceManaged in Compile).value,
      ),
      PB.targets in Compile ++= (mutableGenerators in Compile).value.map(g ⇒ g.target -> (sourceManaged in Compile).value),
      setCodeGenerator := loadAndSetGenerators(
        // the magic sauce: use the output classpath from the the sbt-plugin project and instantiate generator from there
        (fullClasspath in Compile in ProjectRef(file("."), "akka-grpc-sbt-plugin")).value,
        (mutableGenerators in Compile).value
      ),
      PB.recompile in Compile ~= (_ => true)
    )

  case class MutableGeneratorAccess(clazz: String, setUnderlying: protocbridge.ProtocCodeGenerator => Unit, target: (Generator, Seq[String]))
  val setCodeGenerator = taskKey[Unit]("grpc-set-code-generator")
  val mutableGenerators = settingKey[Seq[MutableGeneratorAccess]]("mutable Gens for test")

  def createMutableGenerators(): Seq[MutableGeneratorAccess] = {
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

    object MutableGeneratorAccess {
      def apply(clazz: String): MutableGeneratorAccess = {
        // FIXME: mostly copied from AkkaScalaGrpcCodeGenerator, might be shared with a bit of restructuring
        val adapted = new MutableProtocCodeGenerator
        val id = clazz.split("\\.").last
        val generator = JvmGenerator(s"mutable-$id", adapted)
        val settings: Seq[String] = Seq(
          "flat_package" -> false,
          "java_conversions" -> false,
          "single_line_to_string" -> false).collect { case (settingName, v) if v => settingName }

        new MutableGeneratorAccess(clazz, adapted.setUnderlying _, (generator, settings))
      }
    }

    Seq(
      MutableGeneratorAccess("akka.grpc.sbt.AkkaScalaServerGrpcCodeGenerator"),
      MutableGeneratorAccess("akka.grpc.sbt.AkkaJavaServerGrpcCodeGenerator"),
    )
  }

  def loadAndSetGenerators(classpath: Classpath, access: Seq[MutableGeneratorAccess]): Unit = {
    access.foreach(access ⇒ loadAndSetGenerator(classpath, access))
  }

  def loadAndSetGenerator(classpath: Classpath, access: MutableGeneratorAccess): Unit = {
    val cp = classpath.map(_.data)
    // ensure to set right parent classloader, so that protocbridge.ProtocCodeGenerator etc are
    // compatible with what is already accessible from this sbt build
    val loader = ClasspathUtilities.toLoader(cp, classOf[protocbridge.ProtocCodeGenerator].getClassLoader)
    val instance = loader.loadClass(access.clazz).newInstance()
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
