/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

object ProtocVersion {

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
}
