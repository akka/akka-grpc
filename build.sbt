import akka.grpc.Dependencies
import akka.grpc.ProjectExtensions._
import akka.grpc.build.ReflectiveCodeGen

scalaVersion := "2.12.6"

val commonSettings = Seq(
  organization := "com.lightbend.akka.grpc",

  scalacOptions ++= List(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-encoding", "UTF-8"
  ),
  javacOptions ++= List(
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  )
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
    buildInfoKeys += "runtimeArtifactName" -> akkaGrpcRuntimeName,
    buildInfoKeys += "akkaHttpVersion" → Dependencies.Versions.akkaHttp,
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
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      prependShellScript = Some(sbtassembly.AssemblyPlugin.defaultShellScript)
    ),
  ))
  .settings(addArtifact(artifact in (Compile, assembly), assembly))

lazy val mavenPlugin = Project(
    id = "akka-grpc-maven-plugin",
    base = file("maven-plugin")
  )
  .settings(commonSettings)
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
  .settings(commonSettings)
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
  .pluginTestingSettings
  .settings(
    ReflectiveCodeGen.generatedLanguages := Seq("Scala", "Java"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"),
  )
  // proto files from "io.grpc" % "grpc-interop-testing" contain duplicate Empty definitions;
  // * google/protobuf/empty.proto
  // * io/grpc/testing/integration/empty.proto
  // They have different "java_outer_classname" options, but scalapb does not look at it:
  // https://github.com/scalapb/ScalaPB/issues/243#issuecomment-279769902
  // Therefore we exclude it here.
  .settings(
    excludeFilter in PB.generate := new SimpleFileFilter(
      (f: File) => f.getAbsolutePath.endsWith("google/protobuf/empty.proto"))
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

lazy val playTestdata = Project(
    id="akka-grpc-play-testdata",
    base=file("play-testdata")
  )
  .settings(Dependencies.playTestdata)
  .settings(commonSettings)
  .settings(
    ReflectiveCodeGen.generatedLanguages := Seq("Java", "Scala"),
    ReflectiveCodeGen.extraGenerators := Seq(
      "ScalaMarshallersCodeGenerator",
      "akka.grpc.gen.scaladsl.play.PlayScalaServerCodeGenerator",
      "akka.grpc.gen.scaladsl.play.PlayScalaClientCodeGenerator",
      "akka.grpc.gen.javadsl.play.PlayJavaServerCodeGenerator",
      "akka.grpc.gen.javadsl.play.PlayJavaClientCodeGenerator",
    ),
  )
  .enablePlugins(akka.grpc.NoPublish)
  .pluginTestingSettings

lazy val playTestkit = Project(
    id="akka-grpc-play-testkit",
    base = file("play-testkit")
  )
  .dependsOn(runtime)
  .dependsOn(playTestdata % "test")
  .settings(Dependencies.playTestkit)
  .settings(commonSettings)
  .settings(
    excludeFilter in (Compile, headerSources) := {
      val orig = (excludeFilter in (Test, headerSources)).value
      // The following files have a different license
      orig || "NewGuiceOneServerPerTest.scala" || "NewServerProvider.scala" || "NewBaseOneServerPerTest.scala"
    },
  )
  .pluginTestingSettings

lazy val playInteropTestScala = Project(
    id="akka-grpc-play-interop-test-scala",
    base = file("play-interop-test-scala")
  )
  .dependsOn(playTestkit % "test")
  .settings(Dependencies.playInteropTestScala)
  .settings(commonSettings)
  .settings(
    ReflectiveCodeGen.extraGenerators := Seq(
      "ScalaMarshallersCodeGenerator",
      "akka.grpc.gen.scaladsl.play.PlayScalaServerCodeGenerator",
      "akka.grpc.gen.scaladsl.play.PlayScalaClientCodeGenerator"
    ),
  )
  .enablePlugins(akka.grpc.NoPublish)
  .pluginTestingSettings

lazy val playInteropTestJava = Project(
    id="akka-grpc-play-interop-test-java",
    base = file("play-interop-test-java")
  )
  .dependsOn(playTestkit % "test")
  .settings(Dependencies.playInteropTestJava)
  .settings(commonSettings)
  .settings(
    ReflectiveCodeGen.generatedLanguages := Seq("Java"),
    ReflectiveCodeGen.extraGenerators := Seq(
      "akka.grpc.gen.javadsl.play.PlayJavaServerCodeGenerator",
      "akka.grpc.gen.javadsl.play.PlayJavaClientCodeGenerator",
    ),
  )
  .enablePlugins(akka.grpc.NoPublish)
  .pluginTestingSettings

lazy val docs = Project(
    id = "akka-grpc-docs",
    base = file("docs"),
  )
  // Make sure code generation is ran:
  .dependsOn(pluginTesterScala)
  .dependsOn(pluginTesterJava)
  .enablePlugins(AkkaParadoxPlugin)
  .enablePlugins(akka.grpc.NoPublish)
  .settings(
    // Make sure code generation is ran before paradox:
    (Compile / paradox) := ((Compile / paradox) dependsOn (Compile / compile)).value,
    paradoxGroups := Map(
      "Language" -> Seq("Scala", "Java"),
      "Buildtool" -> Seq("sbt", "Gradle", "Maven"),
    ),
    paradoxProperties ++= Map(
      "grpc.version" → Dependencies.Versions.grpc,
      "akka-http.version" → Dependencies.Versions.akkaHttp,
      "extref.akka-http.base_url" -> s"http://doc.akka.io/docs/akka-http/${Dependencies.Versions.akkaHttp}/%s",
    ),
    resolvers += Resolver.jcenterRepo,
  )

lazy val pluginTesterScala = Project(
  id = "akka-grpc-plugin-tester-scala",
  base = file("plugin-tester-scala")
)
  .settings(Dependencies.pluginTester)
  .settings(commonSettings)
  .enablePlugins(akka.grpc.NoPublish)
  .settings(
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("flat_package")
  )
  .pluginTestingSettings

lazy val pluginTesterJava = Project(
  id = "akka-grpc-plugin-tester-java",
  base = file("plugin-tester-java")
)
  .settings(Dependencies.pluginTester)
  .settings(commonSettings)
  .enablePlugins(akka.grpc.NoPublish)
  .settings(
    ReflectiveCodeGen.generatedLanguages := Seq("Java"),
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
    playInteropTestJava,
    playInteropTestScala,
    playTestkit,
    playTestdata,
    pluginTesterScala,
    pluginTesterJava,
    docs,
  )
  .enablePlugins(akka.grpc.NoPublish)
  .settings(
    unmanagedSources in (Compile, headerCreate) := (baseDirectory.value / "project").**("*.scala").get
  )
