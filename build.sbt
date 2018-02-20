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
  ),

  scriptedLaunchOpts :=
    scriptedLaunchOpts.value ++
      Seq(s"-Dproject.version=${version.value}")

)

val codegenCommon = Project(
  id = "akka-grpc-codegen-common",
  base = file("codegen-common")
).settings(Dependencies.common)
 .settings(commonSettings)

val server = Project(
  id = "akka-grpc-server",
  base = file("server")
).settings(Dependencies.server)
  .settings(commonSettings)
  .dependsOn(codegenCommon)

val interopTests = Project(
  id = "akka-grpc-interop-tests",
  base = file("interop-tests")
).settings(Dependencies.interopTests)
  .settings(commonSettings)
  .enablePlugins(JavaAgent)
  .settings(Seq(
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "test",
  ))
  .dependsOn(server, codegenCommon)

val serverSbtLib = Project(
  id = "akka-grpc-sbt-plugin",
  base = file("sbt-plugin")
).settings(Dependencies.server)
  .settings(commonSettings)
  .dependsOn(server, codegenCommon)

val serverSbt = Project(
  id = "akka-grpc-sbt-plugin",
  base = file("sbt-plugin")
).settings(Dependencies.server)
  .settings(commonSettings)
  .dependsOn(serverSbtLib)

val sbtPluginTester = Project(
  id = "sbt-plugin-tester",
  base = file("sbt-plugin-tester")
).settings(commonSettings)

val aggregatedProjects: Seq[ProjectReference] = Seq(
  server, interopTests, codegenCommon,
  serverSbt, serverSbtLib,
  sbtPluginTester
)

val root = Project(
  id = "akka-grpc",
  base = file(".")
).aggregate(aggregatedProjects: _*)
.settings(
  unmanagedSources in (Compile, headerCreate) := (baseDirectory.value / "project").**("*.scala").get
)
