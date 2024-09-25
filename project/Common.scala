package akka.grpc

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import akka.grpc.Dependencies.Versions.{ scala212, scala213, scala3 }
import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys.projectInfoVersion
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import com.typesafe.tools.mima.plugin.MimaKeys._
import xerial.sbt.Sonatype

object Common extends AutoPlugin {
  override def trigger = allRequirements

  override def requires = JvmPlugin && Sonatype

  private val consoleDisabledOptions = Seq("-Xfatal-warnings", "-Ywarn-unused", "-Ywarn-unused-import")

  override def globalSettings =
    Seq(
      organization := "com.lightbend.akka.grpc",
      organizationName := "Lightbend Inc.",
      organizationHomepage := Some(url("https://www.lightbend.com/")),
      resolvers += "Akka library repository".at("https://repo.akka.io/maven"),
      homepage := Some(url("https://akka.io/")),
      scmInfo := Some(ScmInfo(url("https://github.com/akka/akka-grpc"), "git@github.com:akka/akka-grpc")),
      developers += Developer(
        "contributors",
        "Contributors",
        "https://gitter.im/akka/dev",
        url("https://github.com/akka/akka-grpc/graphs/contributors")),
      releaseNotesURL := (
        // can't use isSnapshot for some reason related to the trixery we do with benchmark-java including modules from this project
        if (version.value.endsWith("SNAPSHOT")) None
        else Some(url(s"https://github.com/akka/akka-grpc/releases/tag/v${version.value}"))
      ),
      licenses := {
        val tagOrBranch =
          if (version.value.endsWith("SNAPSHOT")) "main"
          else "v" + version.value
        Seq(("BUSL-1.1", url(s"https://raw.githubusercontent.com/akka/akka-grpc/${tagOrBranch}/LICENSE")))
      },
      description := "Akka gRPC - Support for building streaming gRPC servers and clients on top of Akka Streams.")

  override lazy val projectSettings = Seq(
    projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
    scalacOptions ++= Seq(
      "-release",
      "11",
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-Xfatal-warnings",
      "-feature",
      "-encoding",
      "UTF-8") ++
    (if (scalaVersion.value.startsWith("2"))
       Seq("-Ywarn-unused")
     else
       Seq.empty),
    Compile / scalacOptions ++=
      Seq(
        // Generated code for methods/fields marked 'deprecated'
        "-Wconf:msg=Marked as deprecated in proto file:silent",
        // deprecated in 2.13, but used as long as we support 2.12
        "-Wconf:msg=Use `scala.jdk.CollectionConverters` instead:silent",
        "-Wconf:msg=Use LazyList instead of Stream:silent",
        "-Wconf:msg=never used:silent") ++
      (if (scalaVersion.value.startsWith("2.12"))
         Seq(
           // we need some nowarns for Scala 3, for things not deprecated yet in 2.12
           "-Wconf:msg=@nowarn annotation does not suppress any warnings:s",
           // ignore imports in templates (FIXME why is that trailing .* needed?)
           "-Wconf:src=.*.txt.*:silent")
       else if (scalaVersion.value.startsWith("2.13")) {
         Seq(
           // ignore imports in templates (FIXME why is that trailing .* needed?)
           "-Wconf:src=.*.txt.*:silent")
       } else {
         // Scala 3
         Seq.empty
       }),
    Compile / console / scalacOptions ~= (_.filterNot(consoleDisabledOptions.contains)),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation", "--release", "11"),
    Compile / doc / scalacOptions :=
      scalacOptions.value ++
      Seq(
        "-doc-title",
        "Akka gRPC",
        "-doc-version",
        version.value,
        "-sourcepath",
        (ThisBuild / baseDirectory).value.toString,
        "-doc-source-url", {
          val branch = if (isSnapshot.value) "main" else s"v${version.value}"
          s"https://github.com/akka/akka-grpc/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
        },
        "-doc-canonical-base-url",
        "https://doc.akka.io/api/akka-grpc/current/") ++
      (if (scalaBinaryVersion.value.startsWith("3")) {
         Seq(
           s"-external-mappings:https://docs.oracle.com/en/java/javase/${Dependencies.JavaDocLinkVersion}/docs/api/java.base/")
       } else {
         Seq(
           "-jdk-api-doc-base",
           s"https://docs.oracle.com/en/java/javase/${Dependencies.JavaDocLinkVersion}/docs/api/java.base/",
           // for some reason Scaladoc creates this
           "-skip-packages",
           "akka.pattern")
       }),
    Compile / doc / scalacOptions -= "-Xfatal-warnings",
    Compile / doc / javacOptions := Seq.empty,
    apiURL := Some(url(s"https://doc.akka.io/api/akka-grpc/${projectInfoVersion.value}/akka/grpc/index.html")),
    (Test / testOptions) += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
    crossScalaVersions := Seq(scala212, scala213, scala3),
    mimaReportSignatureProblems := true,
    scalafmtOnCompile := true)

  val isJdk11orHigher: Boolean = {
    val result = VersionNumber(sys.props("java.specification.version")).matchesSemVer(SemanticSelector(">=11"))
    if (!result)
      throw new IllegalArgumentException("JDK 11 or higher is required")
    result
  }
}
