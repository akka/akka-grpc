package akka.grpc.maven

import java.io.File

import akka.grpc.gen.javadsl.JavaBothCodeGenerator
import akka.grpc.gen.GeneratorAndSettings
import javax.inject.Inject
import org.apache.maven.plugin.AbstractMojo
import org.slf4j.LoggerFactory
import protocbridge.{JvmGenerator, Target}
import org.apache.maven.project.MavenProject

import scala.beans.BeanProperty

class Generate @Inject() (project: MavenProject) extends AbstractMojo {
  val log = LoggerFactory.getLogger(classOf[Generate])

  @BeanProperty
  var protoPath: String = _
  @BeanProperty
  var language: Language = _
  @BeanProperty
  var generateClient: Boolean = _
  @BeanProperty
  var generateServer: Boolean = _

  override def execute(): Unit = {
    // TODO detect .proto files
    val schemas = Set(
      new File(protoPath + "/helloworld.proto").getAbsoluteFile,
    )

    // TODO create the generated protobuf folder in target
    // TODO add the generated folder as a source
    val sourceManaged = new File("target/main/java")
    project.addCompileSourceRoot("target/main/java")

    val akkaGrpcCodeGeneratorSettings = Seq("flat_package")
    val akkaGrpcCodeGenerators = GeneratorAndSettings(JavaBothCodeGenerator, akkaGrpcCodeGeneratorSettings) :: Nil
    val akkaGrpcModelGenerators = Seq[Target](protocbridge.gens.java -> sourceManaged)

    val protocVersion = "-v351"
    val runProtoc: Seq[String] ⇒ Int = args => com.github.os72.protocjar.Protoc.runProtoc(protocVersion +: args.toArray)
    // TODO configuration options
    val includePaths = Seq(new File(protoPath).getAbsoluteFile)
    val dependentProjectsIncludePaths = Seq.empty
    val protocOptions = Seq.empty
    val targets = akkaGrpcModelGenerators ++ akkaGrpcCodeGenerators.map(adaptAkkaGenerator(sourceManaged))
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

  def adaptAkkaGenerator(targetPath: File)(generatorAndSettings: GeneratorAndSettings): Target = {
    val adapted = new ProtocBridgeCodeGenerator(generatorAndSettings.generator)
    val generator = JvmGenerator(generatorAndSettings.generator.name, adapted)
    (generator, generatorAndSettings.settings) -> targetPath
  }
}