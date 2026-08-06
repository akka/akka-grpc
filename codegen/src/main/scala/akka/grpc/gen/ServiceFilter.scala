/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.nio.file.FileSystems

object ServiceFilter {

  final case class Patterns(
      clientInclude: Seq[String],
      clientExclude: Seq[String],
      serverInclude: Seq[String],
      serverExclude: Seq[String])

  // Use the original (non-lowercased) parameter string so service name pattern case
  // is preserved, since grpcName is case-sensitive.
  def parsePatterns(rawParams: String): Patterns = {
    def extract(name: String): Seq[String] =
      s"""(?i)$name=([^,]+)""".r.findFirstMatchIn(rawParams).map(_.group(1).split(";").toSeq).getOrElse(Nil)
    Patterns(
      clientInclude = extract("client_include"),
      clientExclude = extract("client_exclude"),
      serverInclude = extract("server_include"),
      serverExclude = extract("server_exclude"))
  }

  def apply(serviceName: String, include: Seq[String], exclude: Seq[String]): Boolean =
    compile(include, exclude)(serviceName)

  // Precompiles the glob matchers once so the returned predicate can be reused across services
  // without recompiling patterns per call.
  def compile(include: Seq[String], exclude: Seq[String]): String => Boolean = {
    val fs = FileSystems.getDefault
    val includeMatchers = include.map(p => fs.getPathMatcher(s"glob:$p"))
    val excludeMatchers = exclude.map(p => fs.getPathMatcher(s"glob:$p"))
    serviceName => {
      val path = fs.getPath(serviceName)
      val matchesInclude = includeMatchers.isEmpty || includeMatchers.exists(_.matches(path))
      val matchesExclude = excludeMatchers.exists(_.matches(path))
      matchesInclude && !matchesExclude
    }
  }
}
