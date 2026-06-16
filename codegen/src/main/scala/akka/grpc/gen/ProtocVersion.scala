/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

object ProtocVersion {

  sealed trait Alignment
  object Alignment {
    case object Aligned extends Alignment
    final case class Misaligned(message: String) extends Alignment
    final case class Undetermined(message: String) extends Alignment
  }

  private val VersionRegex = """(\d+(?:\.\d+)+|\d+)""".r

  /** The protobuf release "train" of a version string, or None if none can be extracted. */
  def trainOf(version: String): Option[Int] =
    Option(version).flatMap(VersionRegex.findFirstIn).map { v =>
      // protobuf-java is versioned `3.<train>.<patch>` while protoc reports `<train>.<patch>` (train >= 21)
      val segments = v.split('.')
      if (segments(0) == "3" && segments.length >= 3) segments(1).toInt
      else segments(0).toInt
    }

  /** The bare version number of a version string, dropping any non-numeric prefix (e.g. `-v3.25.8` -> `3.25.8`). */
  def display(version: String): String =
    Option(version).flatMap(VersionRegex.findFirstIn).getOrElse(version)

  /** Runs `<executablePath> --version` and returns its reported version, or None if it cannot be queried. */
  def queryVersion(executablePath: String): Option[String] = {
    import scala.sys.process.{ Process, ProcessLogger }
    val output = new StringBuilder
    val logger = ProcessLogger(line => output.append(line).append('\n'), line => output.append(line).append('\n'))
    val exitCode = Process(Seq(executablePath, "--version")).!(logger)
    if (exitCode == 0 && output.nonEmpty) Some(output.toString.trim) else None
  }

  /** Checks whether a protoc executable's reported version belongs to the same protobuf release as the expected version. */
  def checkAlignment(executableLabel: String, expectedVersion: String, reportedVersion: Option[String]): Alignment =
    reportedVersion match {
      case None =>
        Alignment.Undetermined(
          s"Could not determine the version of the configured protoc executable [$executableLabel]; " +
          s"skipping the protoc version alignment check.")
      case Some(reported) =>
        (trainOf(expectedVersion), trainOf(reported)) match {
          case (Some(expected), Some(actual)) if expected != actual =>
            Alignment.Misaligned(
              s"The configured protoc executable [$executableLabel] reports version [$reported] (protobuf $actual.x), " +
              s"which does not match the expected protobuf version [${display(expectedVersion)}] (protobuf $expected.x) " +
              s"that akka-grpc is built against. Mixing protoc and protobuf versions is unsupported and leads to build " +
              s"failures. Please use a protoc from the $expected.x release to align it with the expected protobuf version.")
          case (Some(_), Some(_)) =>
            Alignment.Aligned
          case _ =>
            Alignment.Undetermined(
              s"Could not compare the protoc executable version [$reported] with the expected version " +
              s"[${display(expectedVersion)}]; skipping the protoc version alignment check.")
        }
    }
}
