package akka.grpc.maven

import java.io.File

import akka.grpc.gen.javadsl.{ JavaBothCodeGenerator, JavaClientCodeGenerator, JavaServerCodeGenerator }
import akka.grpc.gen.scaladsl.{ ScalaBothCodeGenerator, ScalaClientCodeGenerator, ScalaServerCodeGenerator }
import akka.grpc.gen.CodeGenerator
import javax.inject.Inject
import org.apache.maven.plugin.AbstractMojo
import org.slf4j.LoggerFactory
import protocbridge.{ JvmGenerator, Target }
import org.apache.maven.project.MavenProject
import scalapb.ScalaPbCodeGenerator

import scala.annotation.tailrec
import scala.beans.BeanProperty

class GenerateMojo @Inject() (project: MavenProject) extends AbstractMojo {
  val log = LoggerFactory.getLogger(classOf[GenerateMojo])

  @BeanProperty
  var protoPath: String = _
  @BeanProperty
  var language: Language = _
  @BeanProperty
  var generateClient: Boolean = _
  @BeanProperty
  var generateServer: Boolean = _

  override def execute(): Unit = {
    val protoDirectory = new File(project.getBasedir, protoPath).getAbsoluteFile
    if (!protoDirectory.exists()) throw new RuntimeException(s"Configured protoPath $protoDirectory does not exist")
    val schemas = findProtoFiles(protoDirectory)

    val sourceRoot = "target/generated-sources/akka-grpc-" + language.name().toLowerCase
    val sourceManaged = new File(sourceRoot)
    project.addCompileSourceRoot(sourceRoot)

    val akkaGrpcCodeGeneratorSettings = Seq("flat_package")
    val targets = language match {
      case Language.JAVA ⇒
        val glueGenerator =
          if (generateServer)
            if (generateClient) JavaBothCodeGenerator
            else JavaServerCodeGenerator
          else JavaClientCodeGenerator
        Seq[Target](
          protocbridge.gens.java -> sourceManaged,
          adaptAkkaGenerator(sourceManaged, glueGenerator, akkaGrpcCodeGeneratorSettings))
      case Language.SCALA ⇒
        val glueGenerator =
          if (generateServer)
            if (generateClient) ScalaBothCodeGenerator
            else ScalaServerCodeGenerator
          else ScalaClientCodeGenerator
        Seq[Target](
          (JvmGenerator("scala", ScalaPbCodeGenerator), akkaGrpcCodeGeneratorSettings) → sourceManaged,
          adaptAkkaGenerator(sourceManaged, glueGenerator, akkaGrpcCodeGeneratorSettings))
    }
    val protocVersion = "-v351"
    val runProtoc: Seq[String] ⇒ Int = args => com.github.os72.protocjar.Protoc.runProtoc(protocVersion +: args.toArray)
    val includePaths = Seq(protoDirectory)

    val dependentProjectsIncludePaths = Seq.empty
    val protocOptions = Seq.empty
    compile(runProtoc, schemas, includePaths ++ dependentProjectsIncludePaths, protocOptions, targets)
  }

  private[this] def executeProtoc(protocCommand: Seq[String] => Int, schemas: Set[File], includePaths: Seq[File], protocOptions: Seq[String], targets: Seq[Target]): Int =
    try {
      val incPath = includePaths.map("-I" + _.getCanonicalPath)
      protocbridge.ProtocBridge.run(protocCommand, targets,
        incPath ++ protocOptions ++ schemas.map(_.getCanonicalPath),
        pluginFrontend = protocbridge.frontend.PluginFrontend.newInstance)
    } catch {
      case e: Exception =>
        throw new RuntimeException("error occurred while compiling protobuf files: %s" format (e.getMessage), e)
    }

  private[this] def compile(protocCommand: Seq[String] => Int, schemas: Set[File], includePaths: Seq[File], protocOptions: Seq[String], targets: Seq[Target]) = {
    // Sort by the length of path names to ensure that delete parent directories before deleting child directories.
    val generatedTargetDirs = targets.map(_.outputPath).sortBy(_.getAbsolutePath.length)
    generatedTargetDirs.foreach(_.mkdirs())

    if (schemas.nonEmpty && targets.nonEmpty) {
      log.info("Compiling %d protobuf files to %s".format(schemas.size, generatedTargetDirs.mkString(",")))
      log.debug("protoc options:")
      protocOptions.map("\t" + _).foreach(log.debug(_))
      schemas.foreach(schema => log.info("Compiling schema %s" format schema))

      val exitCode = executeProtoc(protocCommand, schemas, includePaths, protocOptions, targets)
      if (exitCode != 0)
        sys.error("protoc returned exit code: %d" format exitCode)

      log.info("Compiling protobuf")
      generatedTargetDirs.foreach { dir =>
        log.info("Protoc target directory: %s".format(dir.getAbsolutePath))
      }
    } else if (schemas.nonEmpty && targets.isEmpty) {
      log.info("Protobufs files found, but PB.targets is empty.")
    }
  }

  def adaptAkkaGenerator(targetPath: File, generator: CodeGenerator, settings: Seq[String]): Target = {
    val adapted = new ProtocBridgeCodeGenerator(generator)
    val jvmGenerator = JvmGenerator(generator.name, adapted)
    (jvmGenerator, settings) -> targetPath
  }

  private def findProtoFiles(dir: File): Set[File] = {
    @tailrec
    def find(pending: Array[File], accumulator: Set[File]): Set[File] =
      if (pending.isEmpty)
        accumulator
      else if (pending.head.isDirectory)
        find(pending.head.listFiles(f ⇒ f.isDirectory || f.getName.endsWith(".proto")) ++ pending.tail, accumulator)
      else
        find(pending.tail, accumulator + pending.head)

    find(dir.listFiles(f ⇒ f.isDirectory || f.getName.endsWith(".proto")), Set.empty)
  }

}