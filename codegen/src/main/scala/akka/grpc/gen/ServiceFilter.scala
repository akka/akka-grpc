/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.nio.file.FileSystems

object ServiceFilter {

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
