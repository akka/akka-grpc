package akka.grpc.maven

import java.io.File

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

object GenerateMojo {
  val protocVersion = "-v351"
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

    // generated sources should be compiled
    val generatedSourcesDir = "target/generated-sources/akka-grpc-" + language.name().toLowerCase
    val compileSourceRoot = new File(generatedSourcesDir)
    project.addCompileSourceRoot(generatedSourcesDir)



    // only do any work if needed (m2e integration)
    if (!buildContext.isIncremental && buildContext.hasDelta(generatedSourcesDir)) {
      generate(compileSourceRoot)
    }
  }

  private def generate(generatedSourcesDir: File): Unit = {
    val protoDir = new File(project.getBasedir, protoPath)
    if (!protoDir.exists()) sys.error("Protobuf sources directory [%s] does not exist".format(protoDir))
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
      val akkaGrpcCodeGeneratorSettings = Seq("flat_package")
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
      val includePaths = Seq(new File(protoPath).getAbsoluteFile)
      val protocOptions = Seq.empty

      compile(runProtoc, schemas, includePaths, protocOptions, targets)
    }
  }

  private[this] def executeProtoc(protocCommand: Seq[String] => Int, schemas: Set[File], includePaths: Seq[File], protocOptions: Seq[String], targets: Seq[Target]): Int =
    try {
      val incPath = includePaths.map("-I" + _.getCanonicalPath)
      protocbridge.ProtocBridge.run(protocCommand, targets,
        incPath ++ protocOptions ++ schemas.map(_.getCanonicalPath),
        pluginFrontend = protocbridge.frontend.PluginFrontend.newInstance)
    } catch {
      case e: Exception =>
        throw new RuntimeException("error occurred while compiling protobuf files: %s".format(e.getMessage), e)
    }

  private[this] def compile(protocCommand: Seq[String] => Int, schemas: Set[File], includePaths: Seq[File], protocOptions: Seq[String], targets: Seq[Target]): Unit = {
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

      val exitCode = executeProtoc(protocCommand, schemas, includePaths, protocOptions, targets)
      if (exitCode != 0) {
        // FIXME just logging exit code here isn't very useful, we'd need to get protobridge to capture stderr
        // and report errors in the proto #242
        sys.error("protoc returned exit code: %d".format(exitCode))
      }

      getLog.info("Compiling protobuf")
      generatedTargetDirs.foreach { dir =>
        getLog.info("Protoc target directory: %s".format(dir.getAbsolutePath))
        buildContext.refresh(dir)
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