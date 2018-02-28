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
  .enablePlugins(SbtTwirl, BuildInfoPlugin)
  .settings(Dependencies.common)
  .settings(commonSettings)
  .settings(Seq(
    buildInfoKeys ++= BuildInfoKey.ofN(organization, name, version, scalaVersion, sbtVersion),
    buildInfoKeys += BuildInfoKey.map(projectID in server) { case (_, id) => "runtimeArtifactName" -> CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(id).name },
    buildInfoPackage := "akka.grpc.gen",
  ))

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
  .settings(
    javaAgents += Dependencies.Agents.jettyAlpnAgent % "test",
    // needed explicitly as we don't directly depend on the codegen project
    watchSources ++= (watchSources in codegenCommon).value ++ (watchSources in sbtPlugin).value
  )
  .dependsOn(server)
  .enablePlugins(akka.ReflectiveCodeGen)
  // needed to be able to override the PB.generate task reliably
  .disablePlugins(ProtocPlugin)
  .settings(ProtocPlugin.projectSettings.filterNot(_.a.key.key == PB.generate.key))

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
