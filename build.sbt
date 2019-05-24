import akka.grpc.Dependencies
import akka.grpc.Dependencies.Versions.scala212
import akka.grpc.ProjectExtensions._
import akka.grpc.build.ReflectiveCodeGen

scalaVersion := scala212

val akkaGrpcRuntimeName = "akka-grpc-runtime"

lazy val codegen = Project(
    id = "akka-grpc-codegen",
    base = file("codegen")
  )
  .enablePlugins(SbtTwirl, BuildInfoPlugin)
  .settings(Dependencies.codegen)
  .settings(Seq(
    buildInfoKeys ++= BuildInfoKey.ofN(organization, name, version, scalaVersion, sbtVersion),
    buildInfoKeys += "runtimeArtifactName" -> akkaGrpcRuntimeName,
    buildInfoKeys += "akkaVersion" → Dependencies.Versions.akka,
    buildInfoKeys += "akkaHttpVersion" → Dependencies.Versions.akkaHttp,
    buildInfoKeys += "grpcVersion" → Dependencies.Versions.grpc,
    buildInfoPackage := "akka.grpc.gen",
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    },
    mainClass in assembly := Some("akka.grpc.gen.Main"),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      prependShellScript = Some(sbtassembly.AssemblyPlugin.defaultShellScript)
    ),
  ))
  .settings(addArtifact(artifact in (Compile, assembly), assembly))

lazy val runtime = Project(
    id = akkaGrpcRuntimeName,
    base = file("runtime")
  )
  .settings(Dependencies.runtime)

/** This could be an independent project - or does upstream provide this already? didn't find it.. */
lazy val scalapbProtocPlugin = Project(
    id = "akka-grpc-scalapb-protoc-plugin",
    base = file("scalapb-protoc-plugin")
  )
  /** TODO we only really need to depend on scalapb */
  .dependsOn(codegen)
  .settings(Seq(
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    },
    mainClass in assembly := Some("akka.grpc.scalapb.Main"),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      prependShellScript = Some(sbtassembly.AssemblyPlugin.defaultShellScript)
    ),
  ))
  .settings(addArtifact(artifact in (Compile, assembly), assembly))

lazy val mavenPlugin = Project(
    id = "akka-grpc-maven-plugin",
    base = file("maven-plugin")
  )
  .settings(Dependencies.mavenPlugin)
  .enablePlugins(akka.grpc.SbtMavenPlugin)
  .settings(Seq(
    publishMavenStyle := true,
    crossPaths := false,
  ))
  .dependsOn(codegen)

lazy val sbtPlugin = Project(
    id = "sbt-akka-grpc",
    base = file("sbt-plugin")
  )
  .settings(Dependencies.sbtPlugin)
  .enablePlugins(SbtPlugin)
  .settings(
    publishMavenStyle := false,
    bintrayPackage := "sbt-akka-grpc",
    bintrayRepository := "sbt-plugin-releases",

    /** And for scripted tests: */
    scriptedLaunchOpts += ("-Dproject.version=" + version.value),
    scriptedLaunchOpts ++= sys.props.collect { case (k @ "sbt.ivy.home", v) => s"-D$k=$v" }.toSeq,
    scriptedDependencies := {
      val p1 = publishLocal.value
      val p2 = (publishLocal in codegen).value
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
  .pluginTestingSettings
  .settings(
    ReflectiveCodeGen.generatedLanguages := Seq("Scala", "Java"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"),

    // setting 'skip in publish' would be more elegant, but we need
    // to be able to `publishLocal` to run the interop tests as an
    // sbt scripted test
    whitesourceIgnore := true,
  )
  .settings(
    inConfig(Test)(Seq(
      mainClass in reStart := (mainClass in run in Test).value,
      {
      import spray.revolver.Actions._
      reStart := Def.inputTask{
        restartApp(
          streams.value,
          reLogTag.value,
          thisProjectRef.value,
          reForkOptions.value,
          (mainClass in reStart).value,
          (fullClasspath in reStart).value,
          reStartArgs.value,
          startArgsParser.parsed
        )
      }.dependsOn(products in Compile).evaluated
    }
    )))

lazy val docs = Project(
    id = "akka-grpc-docs",
    base = file("docs"),
  )
  // Make sure code generation is ran:
  .dependsOn(pluginTesterScala)
  .dependsOn(pluginTesterJava)
  .enablePlugins(AkkaParadoxPlugin, ParadoxSitePlugin, PublishRsyncPlugin)
  .settings(
    name := "Akka gRPC",
    skip in publish := true,
    whitesourceIgnore := true,
    previewPath := (Paradox / siteSubdirName).value,
    Paradox / siteSubdirName := s"docs/akka-grpc/${if (isSnapshot.value) "snapshot" else version.value}",
    // Make sure code generation is ran before paradox:
    (Compile / paradox) := ((Compile / paradox) dependsOn (Compile / compile)).value,
    paradoxGroups := Map(
      "Language" -> Seq("Java", "Scala"),
      "Buildtool" -> Seq("sbt", "Gradle", "Maven"),
    ),
    Compile / paradoxProperties ++= Map(
      "akka.version" -> Dependencies.Versions.akka,
      "akka-http.version" -> Dependencies.Versions.akkaHttp,
      "grpc.version" → Dependencies.Versions.grpc,
      "project.url" -> "https://doc.akka.io/docs/akka-grpc/current/",
      "canonical.base_url" -> "https://doc.akka.io/docs/akka-grpc/current",
      "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/current/",
      "extref.akka-docs.base_url" -> s"https://doc.akka.io/docs/akka/${Dependencies.Versions.akka}/%s",
      "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${Dependencies.Versions.akka}",
      "extref.akka-http-docs.base_url" -> s"https://doc.akka.io/docs/akka-http/${Dependencies.Versions.akkaHttp}/%s",
      "scaladoc.akka.http.base_url" -> s"https://doc.akka.io/api/akka-http/${Dependencies.Versions.akkaHttp}/",
    ),
    resolvers += Resolver.jcenterRepo,
    publishRsyncArtifact := makeSite.value -> "www/",
    publishRsyncHost := "akkarepo@gustav.akka.io"
  )

lazy val pluginTesterScala = Project(
  id = "akka-grpc-plugin-tester-scala",
  base = file("plugin-tester-scala")
)
  .settings(Dependencies.pluginTester)
  .settings(
    skip in publish := true,
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("flat_package", "server_power_apis")
  )
  .pluginTestingSettings

lazy val pluginTesterJava = Project(
  id = "akka-grpc-plugin-tester-java",
  base = file("plugin-tester-java")
)
  .settings(Dependencies.pluginTester)
  .settings(
    skip in publish := true,
    ReflectiveCodeGen.generatedLanguages := Seq("Java"),
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("server_power_apis")
  )
  .pluginTestingSettings


lazy val root = Project(
    id = "akka-grpc",
    base = file(".")
  )
  .aggregate(
    runtime,
    codegen,
    mavenPlugin,
    sbtPlugin,
    scalapbProtocPlugin,
    interopTests,
    pluginTesterScala,
    pluginTesterJava,
    docs,
  )
  .settings(
    skip in publish := true,
    unmanagedSources in (Compile, headerCreate) := (baseDirectory.value / "project").**("*.scala").get
  )
