import akka.grpc.Dependencies
import akka.grpc.Dependencies.Versions.{ scala212, scala213 }
import akka.grpc.ProjectExtensions._
import akka.grpc.build.ReflectiveCodeGen
import com.typesafe.tools.mima.core._
import sbt.Keys.scalaVersion

val akkaGrpcRuntimeName = "akka-grpc-runtime"

lazy val mkBatAssemblyTask = taskKey[File]("Create a Windows bat assembly")

// gradle plugin compatibility (avoid `+` in snapshot versions)
dynverSeparator in ThisBuild := "-"

val akkaGrpcCodegenId = "akka-grpc-codegen"
lazy val codegen = Project(id = akkaGrpcCodegenId, base = file("codegen"))
  .enablePlugins(SbtTwirl, BuildInfoPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.codegen)
  .settings(
    mkBatAssemblyTask := {
      val file = assembly.value
      Assemblies.mkBatAssembly(file)
    },
    buildInfoKeys ++= Seq[BuildInfoKey](organization, name, version, scalaVersion, sbtVersion),
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
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript =
      Some(sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = true))),
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := scala212)
  .settings(addArtifact(artifact in (Compile, assembly), assembly))
  .settings(addArtifact(Artifact(akkaGrpcCodegenId, "bat", "bat", "bat"), mkBatAssemblyTask))

lazy val runtime = Project(id = akkaGrpcRuntimeName, base = file("runtime"))
  .settings(Dependencies.runtime)
  .settings(VersionGenerator.settings)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)
  .settings(
    // We don't actually promise binary compatibility before 1.0.0, but want to
    // introduce the tooling
    mimaPreviousArtifacts := Set(organization.value %% "akka-grpc-runtime" % previousStableVersion.value.get),
    AutomaticModuleName.settings("akka.grpc.runtime"),
    ReflectiveCodeGen.generatedLanguages := Seq("Scala"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"))
  .enablePlugins(akka.grpc.build.ReflectiveCodeGen)
  .enablePlugins(ReproducibleBuildsPlugin)

/** This could be an independent project - or does upstream provide this already? didn't find it.. */
val akkaGrpcProtocPluginId = "akka-grpc-scalapb-protoc-plugin"
lazy val scalapbProtocPlugin = Project(id = akkaGrpcProtocPluginId, base = file("scalapb-protoc-plugin"))
  .disablePlugins(MimaPlugin)
  /** TODO we only really need to depend on scalapb */
  .dependsOn(codegen)
  .settings(
    mkBatAssemblyTask := {
      val file = assembly.value
      Assemblies.mkBatAssembly(file)
    },
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    },
    mainClass in assembly := Some("akka.grpc.scalapb.Main"),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript =
      Some(sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = true))))
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .settings(addArtifact(artifact in (Compile, assembly), assembly))
  .settings(addArtifact(Artifact(akkaGrpcProtocPluginId, "bat", "bat", "bat"), mkBatAssemblyTask))
  .enablePlugins(ReproducibleBuildsPlugin)

lazy val mavenPlugin = Project(id = "akka-grpc-maven-plugin", base = file("maven-plugin"))
  .enablePlugins(akka.grpc.SbtMavenPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.mavenPlugin)
  .settings(
    publishMavenStyle := true,
    crossPaths := false,
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .dependsOn(codegen)

lazy val sbtPlugin = Project(id = "sbt-akka-grpc", base = file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.sbtPlugin)
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
    crossSbtVersions := Seq("1.0.0"))
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .dependsOn(codegen)

lazy val interopTests = Project(id = "akka-grpc-interop-tests", base = file("interop-tests"))
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.interopTests)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)
  .pluginTestingSettings
  .settings(
    // All io.grpc servers want to bind to port :8080
    parallelExecution := false,
    ReflectiveCodeGen.generatedLanguages := Seq("Scala", "Java"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"),
    // setting 'skip in publish' would be more elegant, but we need
    // to be able to `publishLocal` to run the interop tests as an
    // sbt scripted test
    whitesourceIgnore := true)
  .settings(inConfig(Test)(Seq(
    mainClass in reStart := (mainClass in run in Test).value, {
      import spray.revolver.Actions._
      reStart := Def
        .inputTask {
          restartApp(
            streams.value,
            reLogTag.value,
            thisProjectRef.value,
            reForkOptions.value,
            (mainClass in reStart).value,
            (fullClasspath in reStart).value,
            reStartArgs.value,
            startArgsParser.parsed)
        }
        .dependsOn(products in Compile)
        .evaluated
    })))

lazy val benchmarks = Project(id = "benchmarks", base = file("benchmarks"))
  .dependsOn(runtime)
  .enablePlugins(JmhPlugin)
  .disablePlugins(MimaPlugin)
  .settings(skip in publish := true)

lazy val docs = Project(id = "akka-grpc-docs", base = file("docs"))
// Make sure code generation is ran:
  .dependsOn(pluginTesterScala)
  .dependsOn(pluginTesterJava)
  .enablePlugins(AkkaParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin, PublishRsyncPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    name := "Akka gRPC",
    publish / skip := true,
    whitesourceIgnore := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/akka-grpc/${projectInfoVersion.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Paradox / siteSubdirName := s"docs/akka-grpc/${projectInfoVersion.value}",
    // Make sure code generation is ran before paradox:
    (Compile / paradox) := (Compile / paradox).dependsOn(Compile / compile).value,
    paradoxGroups := Map("Language" -> Seq("Java", "Scala"), "Buildtool" -> Seq("sbt", "Gradle", "Maven")),
    Compile / paradoxProperties ++= Map(
      "akka.version" -> Dependencies.Versions.akka,
      "akka-http.version" -> Dependencies.Versions.akkaHttp,
      "grpc.version" -> Dependencies.Versions.grpc,
      "project.url" -> "https://doc.akka.io/docs/akka-grpc/current/",
      "canonical.base_url" -> "https://doc.akka.io/docs/akka-grpc/current",
      "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/current/",
      // Akka
      "extref.akka.base_url" -> s"https://doc.akka.io/docs/akka/${Dependencies.Versions.akkaBinary}/%s",
      "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${Dependencies.Versions.akkaBinary}",
      "javadoc.akka.base_url" -> s"https://doc.akka.io/japi/akka/${Dependencies.Versions.akkaBinary}/",
      // Akka HTTP
      "extref.akka-http.base_url" -> s"https://doc.akka.io/docs/akka-http/${Dependencies.Versions.akkaHttpBinary}/%s",
      "scaladoc.akka.http.base_url" -> s"https://doc.akka.io/api/akka-http/${Dependencies.Versions.akkaHttpBinary}/",
      "javadoc.akka.http.base_url" -> s"https://doc.akka.io/japi/akka-http/${Dependencies.Versions.akkaHttpBinary}/",
      // Akka gRPC
      "scaladoc.akka.grpc.base_url" -> s"/${(Preprocess / siteSubdirName).value}/",
      "javadoc.akka.grpc.base_url" -> "" // @apidoc links to Scaladoc
    ),
    apidocRootPackage := "akka",
    resolvers += Resolver.jcenterRepo,
    publishRsyncArtifacts += makeSite.value -> "www/",
    publishRsyncHost := "akkarepo@gustav.akka.io")
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)

lazy val pluginTesterScala = Project(id = "akka-grpc-plugin-tester-scala", base = file("plugin-tester-scala"))
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.pluginTester)
  .settings(
    skip in publish := true,
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := scala212,
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("flat_package", "server_power_apis"))
  .pluginTestingSettings

lazy val pluginTesterJava = Project(id = "akka-grpc-plugin-tester-java", base = file("plugin-tester-java"))
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.pluginTester)
  .settings(
    skip in publish := true,
    ReflectiveCodeGen.generatedLanguages := Seq("Java"),
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := scala212,
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("server_power_apis"))
  .pluginTestingSettings

lazy val root = Project(id = "akka-grpc", base = file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(SitePlugin, MimaPlugin)
  .aggregate(
    runtime,
    codegen,
    mavenPlugin,
    sbtPlugin,
    scalapbProtocPlugin,
    interopTests,
    pluginTesterScala,
    pluginTesterJava,
    docs)
  .settings(
    skip in publish := true,
    unmanagedSources in (Compile, headerCreate) := (baseDirectory.value / "project").**("*.scala").get,
    // unidoc combines sources and jars from all subprojects and that
    // might include some incompatible ones. Depending on the
    // classpath order that might lead to scaladoc compilation errors.
    // the scalapb compilerplugin has a scalapb/package$.class that conflicts
    // with the one from the scalapb runtime, so for that reason we don't produce
    // unidoc for the codegen projects:
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(runtime),
    // https://github.com/sbt/sbt/issues/3465
    // Libs and plugins must share a version. The root project must use that
    // version (and set the crossScalaVersions as empty list) so each sub-project
    // can then decide which scalaVersion and crossCalaVersions they use.
    crossScalaVersions := Nil,
    scalaVersion := scala212)
