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

lazy val codegen = Project(
    id = "akka-grpc-codegen",
    base = file("codegen")
  )
  .enablePlugins(SbtTwirl, BuildInfoPlugin)
  .settings(Dependencies.codegen)
  .settings(commonSettings)
  .settings(Seq(
      buildInfoKeys ++= BuildInfoKey.ofN(organization, name, version, scalaVersion, sbtVersion),
      buildInfoKeys += BuildInfoKey.map(projectID in server) { case (_, id) => "runtimeArtifactName" -> CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(id).name },
      buildInfoPackage := "akka.grpc.gen",
      artifact in (Compile, assembly) := {
        val art = (artifact in (Compile, assembly)).value
        art.withClassifier(Some("assembly"))
      },
      mainClass in assembly := Some("akka.grpc.gen.Main"),
    ) ++ addArtifact(artifact in (Compile, assembly), assembly)
  )

lazy val server = Project(
    id = "akka-grpc-server",
    base = file("server")
  )
  .settings(Dependencies.server)
  .settings(commonSettings)

lazy val sbtPlugin = Project(
    id = "akka-grpc-sbt-plugin",
    base = file("sbt-plugin")
  )
  .settings(commonSettings)
  .settings(
    scriptedLaunchOpts += ("-Dproject.version=" + version.value),
    scriptedDependencies := {
      val p1 = publishLocal.value
      val p2 = (publishLocal in codegen).value

      // 00-interop scripted test dependency
      val p3 = (publishLocal in server).value
      val p4 = (publishLocal in interopTests).value
    },
    scriptedBufferLog := false,
    crossSbtVersions := Seq("1.0.0"),
  )
  .dependsOn(codegen)

lazy val interopTests = Project(
    id = "akka-grpc-interop-tests",
    base = file("interop-tests")
  )
  .settings(Dependencies.interopTests)
  .settings(commonSettings)
  .enablePlugins(JavaAgent)
  .settings(
    javaAgents += Dependencies.Agents.jettyAlpnAgent % "test",
    // needed explicitly as we don't directly depend on the codegen project
    watchSources ++= (watchSources in codegen).value,
    // yeah ugly, but otherwise, there's a circular dependency between the project values
    watchSources ++= (watchSources in ProjectRef(file("."), "akka-grpc-sbt-plugin")).value,
  )
  .dependsOn(server)
  .enablePlugins(akka.ReflectiveCodeGen)
  // needed to be able to override the PB.generate task reliably
  .disablePlugins(ProtocPlugin)
  .settings(ProtocPlugin.projectSettings.filterNot(_.a.key.key == PB.generate.key))

lazy val root = Project(
    id = "akka-grpc",
    base = file(".")
  )
  .aggregate(server, codegen, sbtPlugin, interopTests)
  .settings(
    unmanagedSources in (Compile, headerCreate) := (baseDirectory.value / "project").**("*.scala").get
  )
