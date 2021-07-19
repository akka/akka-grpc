/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

import sys.process.Process

import scala.util.Try

import sbt._
import sbt.Keys._
import sbtwhitesource.WhiteSourcePlugin.autoImport._
import sbtwhitesource._

object Whitesource extends AutoPlugin {
  lazy val gitCurrentBranch =
    Try(Process("git rev-parse --abbrev-ref HEAD").!!.replaceAll("\\s", "")).toOption

  override def requires = WhiteSourcePlugin

  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
    // do not change the value of whitesourceProduct
    whitesourceProduct := "Lightbend Reactive Platform",
    whitesourceAggregateProjectName := {
      val projectName = (LocalRootProject / moduleName).value.replace("-root", "")
      projectName + "-" + (if (isSnapshot.value)
                             if (gitCurrentBranch.contains("main")) "master"
                             else "adhoc"
                           else
                             CrossVersion
                               .partialVersion((LocalRootProject / version).value)
                               .map { case (major, minor) => s"$major.$minor-stable" }
                               .getOrElse("adhoc"))
    },
    whitesourceForceCheckAllDependencies := true,
    whitesourceFailOnError := true)
}
