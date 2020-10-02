import sbt.Keys._
import sbt._

/**
 * Generate version.conf file based on the version setting.
 *
 * This was adapted from https://github.com/akka/akka/blob/v2.6.8/project/VersionGenerator.scala
 */
object VersionGenerator {

  val settings: Seq[Setting[_]] = inConfig(Compile)(
    Seq(
      resourceGenerators += generateVersion(resourceManaged, _ / "akka-grpc-version.conf", """|akka.grpc.version = "%s"
         |""")))

  def generateVersion(dir: SettingKey[File], locate: File => File, template: String) =
    Def.task[Seq[File]] {
      val file = locate(dir.value)
      val content = template.stripMargin.format(version.value)
      if (!file.exists || IO.read(file) != content) IO.write(file, content)
      Seq(file)
    }

}
