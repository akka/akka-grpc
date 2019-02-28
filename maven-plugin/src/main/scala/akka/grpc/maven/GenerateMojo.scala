/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.maven

import java.io.{ ByteArrayOutputStream, File, PrintStream }

import akka.grpc.gen.javadsl.play.{ PlayJavaClientCodeGenerator, PlayJavaServerCodeGenerator }
import akka.grpc.gen.{ CodeGenerator, Logger }
import akka.grpc.gen.javadsl.{ JavaClientCodeGenerator, JavaInterfaceCodeGenerator, JavaPowerApiInterfaceCodeGenerator, JavaServerCodeGenerator }
import akka.grpc.gen.scaladsl.play.{ PlayScalaClientCodeGenerator, PlayScalaServerCodeGenerator }
import akka.grpc.gen.scaladsl.{ ScalaClientCodeGenerator, ScalaPowerApiTraitCodeGenerator, ScalaServerCodeGenerator, ScalaTraitCodeGenerator }
import javax.inject.Inject
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.project.MavenProject
import org.sonatype.plexus.build.incremental.BuildContext
import protocbridge.{ JvmGenerator, Target }
import scalapb.ScalaPbCodeGenerator

import scala.beans.BeanProperty
import scala.util.control.NoStackTrace

object GenerateMojo {
  val protocVersion = "-v351"

  case class ProtocError(file: String, line: Int, pos: Int, message: String)
  private val ProtocErrorRegex = """(\w+\.\w+):(\d+):(\d+):\s(.*)""".r

  /** @return a left(parsed error) or a right(original error string) if it cannot be parsed */
  def parseError(errorLine: String): Either[ProtocError, String] = {
    errorLine match {
      case ProtocErrorRegex(file, line, pos, message) =>
        Left(ProtocError(file, line.toInt, pos.toInt, message))
      case unknown =>
        Right(unknown)
    }
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
    val t = try {
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

  def parseLanguage(text: String): Language = {
    text.toLowerCase match {
      case "scala" => Scala
      case "java" => Java
      case unknown =>
        throw new IllegalArgumentException("[$unknown] is not a supported language, supported are java or scala")
    }
  }

  val akkaGrpcCodeGeneratorSettings = Seq("flat_package")

}

class GenerateMojo @Inject() (project: MavenProject, buildContext: BuildContext) extends AbstractMojo {

  import GenerateMojo._

  @BeanProperty
  var protoPaths: java.util.List[String] = _
  @BeanProperty
  var language: String = _
  @BeanProperty
  var generateClient: Boolean = _
  @BeanProperty
  var generateServer: Boolean = _
  @BeanProperty
  var generatePlayClient: Boolean = _
  @BeanProperty
  var generatePlayServer: Boolean = _
  @BeanProperty
  var serverPowerApis: Boolean = _
  @BeanProperty
  var usePlayActions: Boolean = _

  override def execute(): Unit = {
    val chosenLanguage = parseLanguage(language)

    var directoryFound = false
    protoPaths.forEach { protoPath =>
      // verify proto dir exists
      val protoDir = new File(project.getBasedir, protoPath)
      if (protoDir.exists()) {
        directoryFound = true
        // generated sources should be compiled
        val generatedSourcesDir = s"target/generated-sources/akka-grpc-${chosenLanguage.targetDirSuffix}"
        val compileSourceRoot = new File(project.getBasedir, generatedSourcesDir)
        project.addCompileSourceRoot(generatedSourcesDir)
        generate(chosenLanguage, compileSourceRoot, protoDir)
      }
    }
    if (!directoryFound) sys.error(s"None of protobuf sources directories $protoPaths do not exist")
  }

  private def generate(language: Language, generatedSourcesDir: File, protoDir: File): Unit = {
    val scanner = buildContext.newScanner(protoDir, true)
    scanner.setIncludes(Array("**/*.proto"))
    scanner.scan()
    val schemas = scanner.getIncludedFiles.map(file => new File(protoDir, file))
      .filter(buildContext.hasDelta)
      .toSet

    // only build if there are changes to the proto files
    if (schemas.isEmpty) {
      getLog.info("No changed or new .proto-files found in [%s], skipping code generation".format(generatedSourcesDir))
    } else {

      val targets = language match {
        case Java ⇒
          val glueGenerators = Seq(
            if (generateServer) Seq(JavaInterfaceCodeGenerator, JavaServerCodeGenerator(serverPowerApis)) else Seq.empty,
            if (generatePlayServer) Seq(JavaInterfaceCodeGenerator, JavaServerCodeGenerator(serverPowerApis), PlayJavaServerCodeGenerator(serverPowerApis, usePlayActions)) else Seq.empty,
            if (serverPowerApis) Seq(JavaPowerApiInterfaceCodeGenerator) else Seq.empty,
            if (generateClient) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator) else Seq.empty,
            if (generatePlayClient) Seq(JavaInterfaceCodeGenerator, PlayJavaClientCodeGenerator) else Seq.empty
          ).flatten.distinct
          Seq[Target](protocbridge.gens.java -> generatedSourcesDir) ++
            glueGenerators.map(g => adaptAkkaGenerator(generatedSourcesDir, g, akkaGrpcCodeGeneratorSettings))
        case Scala ⇒
          val glueGenerators = Seq(
            if (generateServer) Seq(ScalaTraitCodeGenerator, ScalaServerCodeGenerator(serverPowerApis)) else Seq.empty,
            if (generatePlayServer) Seq(ScalaTraitCodeGenerator, ScalaServerCodeGenerator(serverPowerApis), PlayScalaServerCodeGenerator(serverPowerApis, usePlayActions)) else Seq.empty,
            if (serverPowerApis) Seq(ScalaPowerApiTraitCodeGenerator) else Seq.empty,
            if (generateClient) Seq(ScalaTraitCodeGenerator, ScalaClientCodeGenerator) else Seq.empty,
            if (generatePlayClient) Seq(ScalaTraitCodeGenerator, PlayScalaClientCodeGenerator) else Seq.empty
          ).flatten.distinct
          Seq[Target](
            (JvmGenerator("scala", ScalaPbCodeGenerator), akkaGrpcCodeGeneratorSettings) → generatedSourcesDir) ++
            glueGenerators.map(g => adaptAkkaGenerator(generatedSourcesDir, g, akkaGrpcCodeGeneratorSettings))
      }

      val runProtoc: Seq[String] ⇒ Int = args => com.github.os72.protocjar.Protoc.runProtoc(protocVersion +: args.toArray)
      val protocOptions = Seq.empty

      compile(runProtoc, schemas, protoDir, protocOptions, targets)
    }
  }

  private[this] def executeProtoc(protocCommand: Seq[String] => Int, schemas: Set[File], protoDir: File, protocOptions: Seq[String], targets: Seq[Target]): Int =
    try {
      val incPath = "-I" + protoDir.getCanonicalPath
      protocbridge.ProtocBridge.run(protocCommand, targets,
        Seq(incPath) ++ protocOptions ++ schemas.map(_.getCanonicalPath),
        pluginFrontend = protocbridge.frontend.PluginFrontend.newInstance)
    } catch {
      case e: Exception =>
        throw new RuntimeException("error occurred while compiling protobuf files: %s".format(e.getMessage), e)
    }

  private[this] def compile(protocCommand: Seq[String] => Int, schemas: Set[File], protoDir: File, protocOptions: Seq[String], targets: Seq[Target]): Unit = {
    // Sort by the length of path names to ensure that we have parent directories before sub directories
    val generatedTargetDirs = targets.map(_.outputPath).sortBy(_.getAbsolutePath.length)
    generatedTargetDirs.foreach(_.mkdirs())
    if (schemas.nonEmpty && targets.nonEmpty) {
      getLog.info("Compiling %d protobuf files to %s".format(schemas.size, generatedTargetDirs.mkString(",")))
      schemas.foreach { schema =>
        buildContext.removeMessages(schema)
      }
      getLog.debug("Compiling schemas [%s]".format(schemas.mkString(",")))
      getLog.debug("protoc options: %s".format(protocOptions.mkString(",")))

      getLog.info("Compiling protobuf")
      val (out, err, exitCode) = captureStdOutAnderr {
        executeProtoc(protocCommand, schemas, protoDir, protocOptions, targets)
      }
      if (exitCode != 0) {
        err.split("\n\r").map(_.trim).map(parseError).foreach {
          case Left(ProtocError(file, line, pos, message)) =>
            buildContext.addMessage(new File(protoDir, file), line, pos, message, BuildContext.SEVERITY_ERROR, new RuntimeException("protoc compilation failed") with NoStackTrace)
          case Right(otherError) =>
            sys.error(s"protoc exit code $exitCode: $otherError")
        }
      } else {
        if (getLog.isDebugEnabled) {
          getLog.debug("protoc output: " + out)
          getLog.debug("protoc stderr: " + err)
        }
        generatedTargetDirs.foreach { dir =>
          getLog.info("Protoc target directory: %s".format(dir.getAbsolutePath))
          buildContext.refresh(dir)
        }
      }
    } else if (schemas.nonEmpty && targets.isEmpty) {
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
