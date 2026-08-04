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

  /** Runs `<executablePath> --version` and returns its reported version; throws if the executable cannot be run. */
  def queryVersion(executablePath: String): String = {

    import java.io.File
    import scala.sys.process.{ BasicIO, Process, ProcessIO }
    import scala.util.control.NonFatal

    if (executablePath.contains(File.separator) && !new File(executablePath).exists())
      throw new RuntimeException(s"The configured protoc executable [$executablePath] does not exist.")

    val output = new StringBuffer
    val io = new ProcessIO(BasicIO.input(connect = false), BasicIO.processFully(output), BasicIO.processFully(output))

    val exitCode =
      try Process(Seq(executablePath, "--version")).run(io).exitValue()
      catch {
        case NonFatal(e) =>
          throw new RuntimeException(
            s"Could not run the configured protoc executable [$executablePath] to determine its version: ${e.getMessage}",
            e)
      }

    if (exitCode != 0)
      throw new RuntimeException(
        s"The configured protoc executable [$executablePath] exited with code $exitCode when queried with --version.")

    val reported = output.toString.trim
    if (reported.isEmpty)
      throw new RuntimeException(
        s"The configured protoc executable [$executablePath] produced no output when queried with --version.")

    reported
  }

  /** Checks whether a protoc executable's reported version belongs to the same protobuf release as the expected version. */
  def checkAlignment(executableLabel: String, expectedVersion: String, reportedVersion: String): Alignment =
    (trainOf(expectedVersion), trainOf(reportedVersion)) match {

      case (Some(expected), Some(actual)) if expected != actual =>
        Alignment.Misaligned(
          s"The configured protoc executable [$executableLabel] reports version [$reportedVersion] (protobuf $actual.x), " +
          s"which does not match the expected protobuf version [${display(expectedVersion)}] (protobuf $expected.x) " +
          s"that akka-grpc is built against. Mixing protoc and protobuf versions is unsupported and leads to build " +
          s"failures. Please use a protoc from the $expected.x release to align it with the expected protobuf version.")

      case (Some(_), Some(_)) =>
        Alignment.Aligned

      case _ =>
        Alignment.Undetermined(
          s"Could not compare the protoc executable version [$reportedVersion] with the expected version " +
          s"[${display(expectedVersion)}]; skipping the protoc version alignment check.")
    }

  private val verified = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  /**
   * Verifies that the protoc executable matches the expected version,
   * at most once per pair in this JVM; throws on a mismatch.
   */
  def verify(executablePath: String, expectedVersion: String, warnFunc: String => Unit): Unit = {
    val key = s"$executablePath@$expectedVersion"
    // only successful checks are cached, so a mismatch keeps failing until the configuration is fixed
    if (!verified.contains(key))
      checkAlignment(executablePath, expectedVersion, queryVersion(executablePath)) match {
        case Alignment.Misaligned(message)   => throw new RuntimeException(message)
        case Alignment.Undetermined(message) => warnFunc(message); verified.add(key)
        case Alignment.Aligned               => verified.add(key)
      }
  }
}
