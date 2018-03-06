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

val akkaGrpcRuntimeName = "akka-grpc-runtime"
lazy val codegen = Project(
    id = "akka-grpc-codegen",
    base = file("codegen")
  )
  .enablePlugins(SbtTwirl, BuildInfoPlugin)
  .settings(Dependencies.codegen)
  .settings(commonSettings)
  .settings(Seq(
    buildInfoKeys ++= BuildInfoKey.ofN(organization, name, version, scalaVersion, sbtVersion),
    buildInfoKeys += "runtimeArtifactName" -> s"${akkaGrpcRuntimeName}_${scalaBinaryVersion.value}",
    buildInfoPackage := "akka.grpc.gen",
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    },
    mainClass in assembly := Some("akka.grpc.gen.Main"),
    ) ++ addArtifact(artifact in (Compile, assembly), assembly)
  )

lazy val runtime = Project(
    id = akkaGrpcRuntimeName,
    base = file("runtime")
  )
  .settings(Dependencies.runtime)
  .settings(commonSettings)

/** This could be an independent project - or does upstream provide this already? didn't find it.. */
lazy val scalapbProtocPlugin = Project(
    id = "akka-grpc-scalapb-protoc-plugin",
    base = file("scalapb-protoc-plugin")
  )
  /** TODO we only really need to depend on scalapb */
  .dependsOn(codegen)
  .settings(commonSettings)
  .settings(Seq(
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    },
    mainClass in assembly := Some("akka.grpc.scalapb.Main"),
  ) ++ addArtifact(artifact in (Compile, assembly), assembly)
)

lazy val sbtPlugin = Project(
    id = "akka-grpc-sbt-plugin",
    base = file("sbt-plugin")
  )
  .settings(commonSettings)
  .settings(Dependencies.sbtPlugin)
  .settings(
    Keys.sbtPlugin := true,
    publishTo := Some(Classpaths.sbtPluginReleases),
    publishMavenStyle := false,

    /** And for scripted tests: */
    scriptedLaunchOpts += ("-Dproject.version=" + version.value),
    scriptedDependencies := {
      val p1 = publishLocal.value
      val p2 = (publishLocal in codegen).value

      // 00-interop scripted test dependency
      val p3 = (publishLocal in runtime).value
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
  .dependsOn(runtime)
  .enablePlugins(akka.ReflectiveCodeGen)
  // needed to be able to override the PB.generate task reliably
  .disablePlugins(ProtocPlugin)
  .settings(ProtocPlugin.projectSettings.filterNot(_.a.key.key == PB.generate.key))

lazy val root = Project(
    id = "akka-grpc",
    base = file(".")
  )
  .aggregate(
    runtime,
    codegen,
    sbtPlugin,
    scalapbProtocPlugin,
    interopTests,
  )
  .settings(
    unmanagedSources in (Compile, headerCreate) := (baseDirectory.value / "project").**("*.scala").get
  )
