package akka.grpc

import java.nio.file.{ Files, Path, Paths }

import scala.jdk.CollectionConverters._
import scala.sys.process._
import scala.util.matching.UnanchoredRegex

import org.eclipse.jgit.diff.RawText
import sbt._
import sbt.Keys._

object VersionSyncCheckPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val grpcVersionSyncCheck = taskKey[Unit]("")
    val googleProtobufVersionSyncCheck = taskKey[Unit]("")
  }
  import autoImport._

  override def globalSettings = Seq(
    grpcVersionSyncCheck := versionSyncCheckImpl(
      "gRPC",
      Dependencies.Versions.grpc,
      raw"""(?i)grpc.?(?i)version.{1,9}(\d+\.\d+\.\d+)""".r.unanchored,
      Seq(
        Paths.get("plugin-tester-java/pom.xml"),
        Paths.get("plugin-tester-scala/pom.xml"),
        Paths.get("sbt-plugin/src/sbt-test/gen-scala-server/00-interop/build.sbt"),
        Paths.get("gradle-plugin/src/main/groovy/akka/grpc/gradle/AkkaGrpcPluginExtension.groovy"),
        Paths.get("native-image-tests/grpc-scala/build.sbt"),
        Paths.get("native-image-tests/grpc-scala/project/plugins.sbt")),
      Set(
        Paths.get("native-image-tests/grpc-scala/build.sbt"),
        Paths.get("native-image-tests/grpc-scala/project/plugins.sbt"),
        Paths.get("samples/akka-grpc-quickstart-java/build.sbt"),
        Paths.get("samples/akka-grpc-quickstart-java/pom.xml"),
        Paths.get("samples/akka-grpc-quickstart-scala/build.sbt"),
        Paths.get("samples/akka-grpc-quickstart-scala/pom.xml"))).value,
    googleProtobufVersionSyncCheck := versionSyncCheckImpl(
      "Google Protobuf",
      Dependencies.Versions.googleProtobuf,
      raw"""(?i)protoc_?version.*= ?"-?v?(\d+\.\d+\.\d+)"""".r.unanchored,
      Seq(
        Paths.get("maven-plugin/src/main/maven/plugin.xml"),
        Paths.get("gradle-plugin/src/main/groovy/akka/grpc/gradle/AkkaGrpcPluginExtension.groovy"))).value)

  def versionSyncCheckImpl(
      name: String,
      expectedVersion: String,
      VersionRegex: UnanchoredRegex,
      knownFiles: Seq[Path],
      ignoredFiles: Set[Path] = Set.empty) =
    Def.task[Unit] {
      val log = state.value.log
      log.info(s"Running $name version sync check, expecting version $expectedVersion")

      def versions(path: Path): (Path, Seq[String]) =
        (
          path,
          Files
            .lines(path)
            .iterator
            .asScala
            .collect({
              case VersionRegex(version) => version
            })
            .toSeq)

      log.info("Sanity checking regex extraction against known files")
      val mismatchVersions =
        knownFiles.filterNot(ignoredFiles).map(versions).filterNot(_._2.toSet == Set(expectedVersion)).toVector
      if (mismatchVersions.isEmpty) {
        log.info("Sanity check passed")
      } else {
        mismatchVersions.foreach {
          case (path, versions) =>
            log.error(s"Found sanity check $name version mismatch: $path -> $versions")
        }
        fail("Sanity check failed")
      }

      val buildBase = (ThisBuild / baseDirectory).value
      val process = Process("git ls-tree -z --full-tree -r --name-only HEAD", buildBase)
      val paths = (process !! log).trim
        .split('\u0000')
        .iterator
        .map(path => Paths.get(path))
        .filter(Files.exists(_))
        .filterNot(ignoredFiles)
        .filterNot(path => RawText.isBinary(Files.newInputStream(path)))
        .filterNot(path => path.toString.endsWith(".enc")) // encrypted blob

      var mismatch = false

      for ((path, versions) <- paths.map(versions(_)).filter(_._2.nonEmpty)) {
        if (versions.forall(_ == expectedVersion)) {
          log.info(s"Found matching $name version $expectedVersion in $path")
        } else {
          log.error(s"Found $name version mismatch: $path -> $versions")
          mismatch = true
        }
      }

      if (mismatch) {
        fail(s"$name version sync check failed, expected $expectedVersion")
      }

      log.info(s"$name version sync check success")
    }

  private def fail(message: String): Nothing = {
    val fail = new MessageOnlyException(message)
    fail.setStackTrace(new Array[StackTraceElement](0))
    throw fail
  }
}
