/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.nio.file.FileSystems

object ServiceFilter {

  final case class Patterns(
      clientInclude: List[String],
      clientExclude: List[String],
      serverInclude: List[String],
      serverExclude: List[String])

  // Use the original (non-lowercased) parameter string so service name pattern case
  // is preserved, since grpcName is case-sensitive.
  def parsePatterns(rawParams: String): Patterns = {
    def extract(name: String): List[String] =
      s"""(?i)$name=([^,]+)""".r.findFirstMatchIn(rawParams).map(_.group(1).split(";").toList).getOrElse(Nil)
    Patterns(
      clientInclude = extract("client_include"),
      clientExclude = extract("client_exclude"),
      serverInclude = extract("server_include"),
      serverExclude = extract("server_exclude"))
  }

  def apply(serviceName: String, include: Seq[String], exclude: Seq[String]): Boolean = {
    val matchesInclude = include.isEmpty || include.exists(pattern => matchesGlob(serviceName, pattern))
    val matchesExclude = exclude.exists(pattern => matchesGlob(serviceName, pattern))
    matchesInclude && !matchesExclude
  }

  private def matchesGlob(name: String, pattern: String): Boolean = {
    val fs = FileSystems.getDefault
    val matcher = fs.getPathMatcher(s"glob:$pattern")
    matcher.matches(fs.getPath(name))
  }
}
