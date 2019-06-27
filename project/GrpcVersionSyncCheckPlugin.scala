package akka.grpc

import java.nio.file.{ Files, Path, Paths }

import scala.collection.JavaConverters._
import scala.sys.process._

import org.eclipse.jgit.diff.RawText
import sbt._
import sbt.Keys._

object GrpcVersionSyncCheckPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val grpcVersionSyncCheck = taskKey[Unit]("")
  }
  import autoImport._

  override def globalSettings =
    grpcVersionSyncCheck := grpcVersionSyncCheckImpl.value

  val GrpcVersionRegex = raw"""[Gg][Rr][Pp][Cc].?[Vv]ersion.{1,9}(\d+\.\d+\.\d+)""".r.unanchored

  def grpcVersionSyncCheckImpl = Def.task[Unit] {
    val log = state.value.log
    val expectedVersion = Dependencies.Versions.grpc
    log.info(s"Running gRPC version sync check, expecting version $expectedVersion")

    def grpcVersions(paths: Iterator[Path]): Iterator[(Path, String)] =
      for {
        path <- paths
        lines = Files.lines(path).iterator.asScala
        GrpcVersionRegex(version) <- lines
      } yield path -> version

    log.info("Sanity checking regex extraction against known files")
    val knownFiles = Seq(
      Paths.get("gradle-plugin/src/main/groovy/akka/grpc/gradle/AkkaGrpcPlugin.groovy"),
      Paths.get("plugin-tester-java/pom.xml"),
      Paths.get("plugin-tester-scala/pom.xml"))
    val mismatchVersions = grpcVersions(knownFiles.iterator).filter(_._2 != expectedVersion).toVector
    if (mismatchVersions.isEmpty) {
      log.info("Sanity check passed")
    } else {
      mismatchVersions.foreach {
        case (path, version) =>
          log.error(s"Found sanity check gRPC version mismatch: $path -> $version")
      }
      fail("Sanity check failed")
    }

    val buildBase = (baseDirectory in ThisBuild).value
    val process = Process("git ls-tree -z --full-tree -r --name-only HEAD", buildBase)
    val paths = (process !! log).trim
      .split('\u0000')
      .iterator
      .map(path => Paths.get(path))
      .filterNot(path => RawText.isBinary(Files.newInputStream(path)))
      .filterNot(path => path.toString.endsWith(".enc")) // encrypted blob

    var mismatch = false

    for ((path, version) <- grpcVersions(paths)) {
      if (version == expectedVersion) {
        log.info(s"Found matching gRPC version $version in $path")
      } else {
        log.error(s"Found gRPC version mismatch: $path -> $version")
        mismatch = true
      }
    }

    if (mismatch) {
      fail(s"gRPC version sync check failed, expected $expectedVersion")
    }

    log.info(s"gRPC version sync check success")
  }

  private def fail(message: String): Nothing = {
    val fail = new MessageOnlyException(message)
    fail.setStackTrace(new Array[StackTraceElement](0))
    throw fail
  }
}
