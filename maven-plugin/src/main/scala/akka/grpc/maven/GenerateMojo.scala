package akka.grpc.maven

import java.io.{ ByteArrayOutputStream, File, PrintStream }

import akka.grpc.gen.CodeGenerator
import akka.grpc.gen.javadsl.{ JavaBothCodeGenerator, JavaClientCodeGenerator, JavaServerCodeGenerator }
import akka.grpc.gen.scaladsl.{ ScalaBothCodeGenerator, ScalaClientCodeGenerator, ScalaServerCodeGenerator }
import javax.inject.Inject
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.project.MavenProject
import org.sonatype.plexus.build.incremental.BuildContext
import protocbridge.{ JvmGenerator, Target }
import scalapb.ScalaPbCodeGenerator

import scala.annotation.tailrec
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

  val akkaGrpcCodeGeneratorSettings = Seq("flat_package")

}

class GenerateMojo @Inject() (project: MavenProject, buildContext: BuildContext) extends AbstractMojo {
  import GenerateMojo._

  @BeanProperty
  var protoPath: String = _
  @BeanProperty
  var language: Language = _
  @BeanProperty
  var generateClient: Boolean = _
  @BeanProperty
  var generateServer: Boolean = _

  override def execute(): Unit = {
    // verify proto dir exists
    val protoDir = new File(project.getBasedir, protoPath)
    if (!protoDir.exists()) sys.error("Protobuf sources directory [%s] does not exist".format(protoDir))

    // generated sources should be compiled
    val generatedSourcesDir = "target/generated-sources/akka-grpc-" + language.name().toLowerCase
    val compileSourceRoot = new File(project.getBasedir, generatedSourcesDir)
    project.addCompileSourceRoot(generatedSourcesDir)

    generate(compileSourceRoot, protoDir)
  }

  private def generate(generatedSourcesDir: File, protoDir: File): Unit = {

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
        case Language.JAVA ⇒
          val glueGenerator =
            if (generateServer)
              if (generateClient) JavaBothCodeGenerator
              else JavaServerCodeGenerator
            else JavaClientCodeGenerator
          Seq[Target](
            protocbridge.gens.java -> generatedSourcesDir,
            adaptAkkaGenerator(generatedSourcesDir, glueGenerator, akkaGrpcCodeGeneratorSettings))
        case Language.SCALA ⇒
          val glueGenerator =
            if (generateServer)
              if (generateClient) ScalaBothCodeGenerator
              else ScalaServerCodeGenerator
            else ScalaClientCodeGenerator
          Seq[Target](
            (JvmGenerator("scala", ScalaPbCodeGenerator), akkaGrpcCodeGeneratorSettings) → generatedSourcesDir,
            adaptAkkaGenerator(generatedSourcesDir, glueGenerator, akkaGrpcCodeGeneratorSettings))
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
    val adapted = new ProtocBridgeCodeGenerator(generator)
    val jvmGenerator = JvmGenerator(generator.name, adapted)
    (jvmGenerator, settings) -> targetPath
  }

}