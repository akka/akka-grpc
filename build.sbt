import akka.Dependencies

scalaVersion := "2.12.4"

val commonSettings = Seq(
  organization := "com.lightbend.akka.grpc",

  scalacOptions ++= List(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-encoding", "UTF-8"
  ),

  // For the akka-http snapshot
  resolvers += Resolver.bintrayRepo("akka", "maven"),
)

lazy val codegenCommon = Project(
    id = "akka-grpc-codegen-common",
    base = file("codegen-common")
  )
  .settings(Dependencies.common)
  .settings(commonSettings)

lazy val server = Project(
    id = "akka-grpc-server",
    base = file("server")
  )
  .settings(Dependencies.server)
  .settings(commonSettings)

val interopTests = Project(
    id = "akka-grpc-interop-tests",
    base = file("interop-tests")
  )
  .settings(Dependencies.interopTests)
  .settings(commonSettings)
  .enablePlugins(JavaAgent)
  .settings(Seq(
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "test",
  ))
  .dependsOn(server)

lazy val sbtPlugin = Project(
    id = "akka-grpc-sbt-plugin",
    base = file("sbt-plugin")
  )
  .settings(commonSettings)
  .settings(
    scriptedLaunchOpts += ("-Dproject.version=" + version.value),
    scriptedDependencies := {
      val p1 = publishLocal.value
      val p2 = (publishLocal in codegenCommon).value

      // 00-interop scripted test dependency
      val p3 = (publishLocal in server).value
      val p4 = (publishLocal in interopTests).value
    },
    scriptedBufferLog := false,
    crossSbtVersions := Seq("1.0.0"),
  )
  .dependsOn(codegenCommon)

val aggregatedProjects: Seq[ProjectReference] = Seq(
  server, interopTests,
  codegenCommon,
  sbtPlugin
)

lazy val root = Project(
    id = "akka-grpc",
    base = file(".")
  )
  .aggregate(aggregatedProjects: _*)
  .settings(
    unmanagedSources in (Compile, headerCreate) := (baseDirectory.value / "project").**("*.scala").get
  )
