/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.maven

import java.io.{ ByteArrayOutputStream, File, PrintStream }
import akka.grpc.gen.{ CodeGenerator, Logger, ProtocSettings }
import akka.grpc.gen.javadsl.{ JavaClientCodeGenerator, JavaInterfaceCodeGenerator, JavaServerCodeGenerator }
import akka.grpc.gen.scaladsl.{ ScalaClientCodeGenerator, ScalaServerCodeGenerator, ScalaTraitCodeGenerator }

import javax.inject.Inject
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.project.MavenProject
import org.sonatype.plexus.build.incremental.BuildContext
import protocbridge.{ JvmGenerator, ProtocRunner, Target }
import scalapb.ScalaPbCodeGenerator

import scala.beans.BeanProperty
import scala.util.control.NoStackTrace

object AbstractGenerateMojo {
  case class ProtocError(file: String, line: Int, pos: Int, message: String)
  private val ProtocErrorRegex = """(\w+\.\w+):(\d+):(\d+):\s(.*)""".r

  /** @return a left(parsed error) or a right(original error string) if it cannot be parsed */
  def parseError(errorLine: String): Either[ProtocError, String] =
    errorLine match {
      case ProtocErrorRegex(file, line, pos, message) =>
        Left(ProtocError(file, line.toInt, pos.toInt, message))
      case unknown =>
        Right(unknown)
    }

  private def captureStdOutAnderr[T](block: => T): (String, String, T) = {
    val errBao = new ByteArrayOutputStream()
    val errPrinter = new PrintStream(errBao, true, "UTF-8")
    val outBao = new ByteArrayOutputStream()
    val outPrinter = new PrintStream(outBao, true, "UTF-8")
    val originalOut = System.out
    val originalErr = System.err
    System.setOut(outPrinter)
    System.setErr(errPrinter)
    val t =
      try {
        block
      } finally {
        System.setOut(originalOut)
        System.setErr(originalErr)
      }

    (outBao.toString("UTF-8"), errBao.toString("UTF-8"), t)
  }

  sealed trait Language {
    def targetDirSuffix: String
  }
  case object Scala extends Language {
    val targetDirSuffix = "scala"
  }
  case object Java extends Language {
    val targetDirSuffix = "java"
  }

  def parseLanguage(text: String): Language =
    text.toLowerCase match {
      case "scala" => Scala
      case "java"  => Java
      case unknown =>
        throw new IllegalArgumentException("[" + unknown + "] is not a supported language, supported are java or scala")
    }

  /**
   * Turns generatorSettings into sequence of strings, including to:
   * 1. Filter keys if the values are not false.
   * 2. Make camelCase into snake_case
   * e.g. { "flatPackage": "true", "serverPowerApis": "false" } -> ["flat_package"]
   */
  def parseGeneratorSettings(generatorSettings: java.util.Map[String, String]): Seq[String] = {
    import scala.jdk.CollectionConverters._
    generatorSettings.asScala.filter(_._2.toLowerCase() != "false").keys.toSeq.map { params =>
      "[A-Z]".r.replaceAllIn(params, (s => s"_${s.group(0).toLowerCase()}"))
    }
  }
}

abstract class AbstractGenerateMojo @Inject() (buildContext: BuildContext) extends AbstractMojo {
  import AbstractGenerateMojo._

  @BeanProperty
  var project: MavenProject = _

  @BeanProperty
  var protoPaths: java.util.List[String] = _
  @BeanProperty
  var outputDirectory: String = _
  @BeanProperty
  var language: String = _
  @BeanProperty
  var generateClient: Boolean = _
  @BeanProperty
  var generateServer: Boolean = _

  // Add the 'akka.grpc.gen.javadsl.play.PlayJavaClientCodeGenerator' or 'akka.grpc.gen.scaladsl.play.PlayScalaClientCodeGenerator' extra generator instead
  @Deprecated
  @BeanProperty
  var generatePlayClient: Boolean = _
  // Add the 'akka.grpc.gen.javadsl.play.PlayJavaServerCodeGenerator' or 'akka.grpc.gen.scaladsl.play.PlayScalaServerCodeGenerator' extra generator instead
  @Deprecated
  @BeanProperty
  var generatePlayServer: Boolean = _

  import scala.jdk.CollectionConverters._
  @BeanProperty
  var generatorSettings: java.util.Map[String, String] = _

  @BeanProperty
  var extraGenerators: java.util.ArrayList[String] = _

  @BeanProperty
  var includeStdTypes: Boolean = _

  @BeanProperty
  var protocVersion: String = _

  def addGeneratedSourceRoot(generatedSourcesDir: String): Unit

  //https://maven.apache.org/plugin-developers/common-bugs.html#Resolving_Relative_Paths
  def normalize(protoPath: String): File = {
    val protoFile = new File(protoPath)
    if (!protoFile.isAbsolute()) {
      new File(project.getBasedir(), protoPath).toPath().normalize().toFile()
    } else {
      protoFile
    }
  }

  def normalizedProtoPaths = protoPaths.asScala.map(normalize)

  override def execute(): Unit = {
    val chosenLanguage = parseLanguage(language)

    var directoryFound = false

    normalizedProtoPaths.foreach { protoDir =>
      // verify proto dir exists
      if (protoDir.exists()) {
        directoryFound = true
        // generated sources should be compiled
        val generatedSourcesDir = s"${outputDirectory}/akka-grpc-${chosenLanguage.targetDirSuffix}"
        val compileSourceRoot = {
          val generatedSourcesFile = new File(generatedSourcesDir)
          if (!generatedSourcesFile.isAbsolute()) {
            new File(project.getBasedir(), generatedSourcesDir).toPath().normalize().toFile()
          } else {
            generatedSourcesFile
          }
        }
        addGeneratedSourceRoot(generatedSourcesDir)
        generate(chosenLanguage, compileSourceRoot, protoDir)
      }
    }
    if (!directoryFound) sys.error(s"None of protobuf sources directories $protoPaths do not exist")
  }

  private def generate(language: Language, generatedSourcesDir: File, protoDir: File): Unit = {
    val scanner = buildContext.newScanner(protoDir, true)
    scanner.setIncludes(Array("**/*.proto"))
    scanner.scan()
    val schemas = scanner.getIncludedFiles.map(file => new File(protoDir, file)).filter(buildContext.hasDelta).toSet

    // only build if there are changes to the proto files
    if (schemas.isEmpty) {
      getLog.info("No changed or new .proto-files found in [%s], skipping code generation".format(generatedSourcesDir))
    } else {
      val loadedExtraGenerators =
        extraGenerators.asScala.map(cls =>
          Class.forName(cls).getDeclaredConstructor().newInstance().asInstanceOf[CodeGenerator])

      val targets = language match {
        case Java =>
          val glueGenerators = loadedExtraGenerators ++ Seq(
            if (generateServer) Seq(JavaInterfaceCodeGenerator, JavaServerCodeGenerator) else Seq.empty,
            if (generateClient) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator)
            else Seq.empty).flatten.distinct

          val settings = parseGeneratorSettings(generatorSettings)
          val javaSettings = settings.intersect(ProtocSettings.protocJava)

          Seq[Target](Target(protocbridge.gens.java, generatedSourcesDir, javaSettings)) ++
          glueGenerators.map(g => adaptAkkaGenerator(generatedSourcesDir, g, settings))
        case Scala =>
          // Add flatPackage option as default if it's not set.
          val settings =
            if (generatorSettings.containsKey("flatPackage"))
              parseGeneratorSettings(generatorSettings)
            else
              parseGeneratorSettings(generatorSettings) :+ "flat_package"
          val scalapbSettings = settings.intersect(ProtocSettings.scalapb)

          val glueGenerators = Seq(
            if (generateServer) Seq(ScalaTraitCodeGenerator, ScalaServerCodeGenerator) else Seq.empty,
            if (generateClient) Seq(ScalaTraitCodeGenerator, ScalaClientCodeGenerator) else Seq.empty).flatten.distinct
          // TODO whitelist scala generator parameters instead of blacklist
          Seq[Target]((JvmGenerator("scala", ScalaPbCodeGenerator), scalapbSettings) -> generatedSourcesDir) ++
          glueGenerators.map(g => adaptAkkaGenerator(generatedSourcesDir, g, settings))
      }

      val runProtoc: Seq[String] => Int = args =>
        com.github.os72.protocjar.Protoc.runProtoc(protocVersion +: args.toArray)
      val protocOptions = if (includeStdTypes) Seq("--include_std_types") else Seq.empty

      compile(runProtoc, schemas, protoDir, protocOptions, targets)
    }
  }

  private[this] def executeProtoc(
      protocCommand: Seq[String] => Int,
      schemas: Set[File],
      protocOptions: Seq[String],
      targets: Seq[Target]): Int =
    try {
      val protocIncludePaths = normalizedProtoPaths.map { includePath =>
        "-I" + includePath.getCanonicalPath
      }
      protocbridge.ProtocBridge.execute(
        ProtocRunner.fromFunction((args, _) => protocCommand(args)),
        targets,
        protocIncludePaths ++ protocOptions ++ schemas.map(_.getCanonicalPath),
        artifact =>
          throw new RuntimeException(
            s"The version of sbt-protoc you are using is incompatible with '${artifact}' code generator. Please update sbt-protoc to a version >= 0.99.33"))
    } catch {
      case e: Exception =>
        throw new RuntimeException("error occurred while compiling protobuf files: %s".format(e.getMessage), e)
    }

  private[this] def compile(
      protocCommand: Seq[String] => Int,
      schemas: Set[File],
      protoDir: File,
      protocOptions: Seq[String],
      targets: Seq[Target]): Unit = {
    // Sort by the length of path names to ensure that we have parent directories before sub directories
    val generatedTargets = targets
      .map { t =>
        if (!t.outputPath.isAbsolute()) {
          t.copy(outputPath = new File(t.outputPath.getAbsolutePath).toPath().normalize().toFile())
        } else {
          t
        }
      }
      .sortBy(_.outputPath.getAbsolutePath.length)
    generatedTargets.foreach(_.outputPath.mkdirs())
    if (schemas.nonEmpty && generatedTargets.nonEmpty) {
      getLog.info(
        "Compiling %d protobuf files to %s".format(schemas.size, generatedTargets.map(_.outputPath).mkString(",")))
      schemas.foreach { schema => buildContext.removeMessages(schema) }
      getLog.debug("Compiling schemas [%s]".format(schemas.mkString(",")))
      getLog.debug("protoc options: %s".format(protocOptions.mkString(",")))

      getLog.info("Compiling protobuf")
      val (out, err, exitCode) = captureStdOutAnderr {
        executeProtoc(protocCommand, schemas, protocOptions, generatedTargets)
      }
      if (exitCode != 0) {
        err.split("\n\r").map(_.trim).map(parseError).foreach {
          case Left(ProtocError(file, line, pos, message)) =>
            buildContext.addMessage(
              new File(protoDir, file),
              line,
              pos,
              message,
              BuildContext.SEVERITY_ERROR,
              new RuntimeException("protoc compilation failed") with NoStackTrace)
          case Right(otherError) =>
            if (otherError.contains("program not found or is not executable")) {
              sys.error(
                s"Could not execute the automatically downloaded protoc to compile protobuf files, check that the filesystem of " +
                s"[${sys.props("java.io.tmpdir")}] is not mounted with the 'noexec' option, or specify an alternative directory allowing executables using the Java " +
                "system property java.io.tmpdir when executing maven.")
            }
            sys.error(s"protoc exit code $exitCode: $otherError")
        }
      } else {
        if (getLog.isDebugEnabled) {
          getLog.debug("protoc output: " + out)
          getLog.debug("protoc stderr: " + err)
        }
        generatedTargets.foreach { dir =>
          getLog.info("Protoc target directory: %s".format(dir.outputPath.getAbsolutePath))
          buildContext.refresh(dir.outputPath)
        }
      }
    } else if (schemas.nonEmpty && generatedTargets.isEmpty) {
      getLog.info("Protobufs files found, but PB.targets is empty.")
    }
  }

  def adaptAkkaGenerator(targetPath: File, generator: CodeGenerator, settings: Seq[String]): Target = {
    val logger = new Logger {
      def debug(text: String): Unit = getLog.debug(text)
      def info(text: String): Unit = getLog.info(text)
      def warn(text: String): Unit = getLog.warn(text)
      def error(text: String): Unit = getLog.error(text)
    }
    // scala binary version is not used from here, as gradle protoc plugin does not use suggested dependencies
    val adapted = new ProtocBridgeCodeGenerator(generator, CodeGenerator.ScalaBinaryVersion("2.12"), logger)
    val jvmGenerator = JvmGenerator(generator.name, adapted)
    (jvmGenerator, settings) -> targetPath
  }
}
