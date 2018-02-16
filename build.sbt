import akka.Dependencies

import sbt._
import sbt.Keys._

scalaVersion := "2.12.3"

val commonSettings = Seq(
  organization := "com.lightbend.akka.grpc",

  crossSbtVersions := Vector("1.1.1"),

  scalacOptions ++= List(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-encoding", "UTF-8"
  )
)

val codegenCommon = Project(
  id = "akka-grpc-codegen-common",
  base = file("codegen-common")
).settings(Dependencies.common)

val server = Project(
  id = "akka-grpc-server",
  base = file("server")
).settings(Dependencies.server)
  .dependsOn(codegenCommon)

val serverSbtLib = Project(
  id = "akka-grpc-sbt-plugin",
  base = file("sbt-plugin")
).settings(Dependencies.server)
  .dependsOn(server)

val serverSbt = Project(
  id = "akka-grpc-sbt-plugin",
  base = file("sbt-plugin")
).settings(Dependencies.server)
  .dependsOn(serverSbtLib)

val sbtPluginTester = Project(
  id = "sbt-plugin-tester",
  base = file("sbt-plugin-tester")
)

val aggregatedProjects: Seq[ProjectReference] = Seq(
  server, codegenCommon,
  serverSbt, serverSbtLib
)

val root = Project(
  id = "akka-grpc",
  base = file(".")
).aggregate(aggregatedProjects: _*)
