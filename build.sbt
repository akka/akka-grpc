import akka.grpc.Dependencies
import akka.grpc.Dependencies.Versions.scala212
import akka.grpc.ProjectExtensions._
import akka.grpc.build.ReflectiveCodeGen
import sbt.Keys.scalaVersion
import com.geirsson.CiReleasePlugin
import com.typesafe.sbt.site.util.SiteHelpers
import com.jsuereth.sbtpgp.PgpKeys.publishSignedConfiguration

val akkaGrpcRuntimeName = "akka-grpc-runtime"

lazy val mkBatAssemblyTask = taskKey[File]("Create a Windows bat assembly")

// gradle plugin compatibility (avoid `+` in snapshot versions)
ThisBuild / dynverSeparator := "-"
// append -SNAPSHOT to version when isSnapshot
ThisBuild / dynverSonatypeSnapshots := true

// skip Java 9 module info in all assembled artifacts
ThisBuild / assemblyMergeStrategy := {
  case x if x.endsWith("module-info.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

val akkaGrpcCodegenId = "akka-grpc-codegen"
lazy val codegen = Project(id = akkaGrpcCodegenId, base = file("codegen"))
  .enablePlugins(SbtTwirl, BuildInfoPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin, CiReleasePlugin)
  .settings(Dependencies.codegen)
  .settings(resolvers += Resolver.sbtPluginRepo("releases"))
  .settings(
    mkBatAssemblyTask := {
      val file = assembly.value
      Assemblies.mkBatAssembly(file)
    },
    buildInfoKeys ++= Seq[BuildInfoKey](organization, name, version, scalaVersion, sbtVersion),
    buildInfoKeys += "runtimeArtifactName" -> akkaGrpcRuntimeName,
    buildInfoKeys += "akkaVersion" -> Dependencies.Versions.akka,
    buildInfoKeys += "akkaHttpVersion" -> Dependencies.Versions.akkaHttp,
    buildInfoKeys += "grpcVersion" -> Dependencies.Versions.grpc,
    buildInfoKeys += "googleProtobufVersion" -> Dependencies.Versions.googleProtobuf,
    buildInfoPackage := "akka.grpc.gen",
    (Compile / assembly / artifact) := {
      val art = (Compile / assembly / artifact).value
      art.withClassifier(Some("assembly"))
    },
    (assembly / mainClass) := Some("akka.grpc.gen.Main"),
    (assembly / assemblyOption) := (assembly / assemblyOption).value.withPrependShellScript(
      Some(sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = true))),
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .settings(addArtifact((Compile / assembly / artifact), assembly))
  .settings(addArtifact(Artifact(akkaGrpcCodegenId, "bat", "bat", "bat"), mkBatAssemblyTask))

lazy val runtime = Project(id = akkaGrpcRuntimeName, base = file("runtime"))
  .settings(Dependencies.runtime)
  .settings(VersionGenerator.settings)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head,
    mimaFailOnNoPrevious := true,
    mimaPreviousArtifacts := previousStableVersion.value
      .map(v => Set(organization.value %% "akka-grpc-runtime" % v))
      .getOrElse(Set.empty),
    AutomaticModuleName.settings("akka.grpc.runtime"),
    ReflectiveCodeGen.generatedLanguages := Seq("Scala"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"),
    PB.protocVersion := Dependencies.Versions.googleProtobuf,
    Test / PB.targets += (scalapb.gen() -> (Test / sourceManaged).value))
  .enablePlugins(akka.grpc.build.ReflectiveCodeGen)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(CiReleasePlugin)

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
    (Compile / assembly / artifact) := {
      val art = (Compile / assembly / artifact).value
      art.withClassifier(Some("assembly"))
    },
    (assembly / mainClass) := Some("akka.grpc.scalapb.Main"),
    (assembly / assemblyOption) := (assembly / assemblyOption).value.withPrependShellScript(
      Some(sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = true))),
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .settings(addArtifact((Compile / assembly / artifact), assembly))
  .settings(addArtifact(Artifact(akkaGrpcProtocPluginId, "bat", "bat", "bat"), mkBatAssemblyTask))
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(CiReleasePlugin)

lazy val mavenPlugin = Project(id = "akka-grpc-maven-plugin", base = file("maven-plugin"))
  .enablePlugins(akka.grpc.SbtMavenPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin, CiReleasePlugin)
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
  .disablePlugins(MimaPlugin, CiReleasePlugin)
  .settings(Dependencies.sbtPlugin)
  .settings(
    /** And for scripted tests: */
    scriptedLaunchOpts += ("-Dproject.version=" + version.value),
    scriptedLaunchOpts ++= sys.props.collect { case (k @ "sbt.ivy.home", v) => s"-D$k=$v" }.toSeq,
    scriptedLaunchOpts ++= {
      // pass along token repo to scripted test projects (scripted tests are isolated and not picking that up from
      // global sbt config)
      val akkaRepo = resolvers.value.collectFirst {
        case repo: MavenRepository if repo.root.contains("secure") => repo.root
      }.orElse(resolvers.value.collectFirst {
        case repo: MavenRepository if repo.root.contains("github_actions") => repo.root
      })
      akkaRepo.map(repo => s"-Dscripted.resolver=$repo")
    },
    scriptedDependencies := {
      val p1 = publishLocal.value
      val p2 = (codegen / publishLocal).value
      val p3 = (runtime / publishLocal).value
      val p4 = (interopTests / publishLocal).value
    },
    scriptedBufferLog := false,
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .dependsOn(codegen)

lazy val interopTests = Project(id = "akka-grpc-interop-tests", base = file("interop-tests"))
  .disablePlugins(MimaPlugin, CiReleasePlugin)
  .settings(Dependencies.interopTests)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)
  .pluginTestingSettings
  .settings(
    // All io.grpc servers want to bind to port :8080
    Test / parallelExecution := false,
    ReflectiveCodeGen.generatedLanguages := Seq("Scala", "Java"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"),
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("server_power_apis"),
    // grpc-interop pulls in proto files with unfulfilled transitive deps it seems
    // FIXME descriptor.proto is excluded because of EnumType issue https://github.com/scalapb/ScalaPB/issues/1557
    PB.generate / excludeFilter := new SimpleFileFilter((f: File) =>
      f.getAbsolutePath.endsWith("google/protobuf/descriptor.proto") ||
      f.getParent.contains("envoy")),
    PB.protocVersion := Dependencies.Versions.googleProtobuf,
    // This project should use 'publish/skip := true', but we need
    // to be able to `publishLocal` to run the interop tests as an
    // sbt scripted test. At least skip scaladoc generation though.
    Compile / doc := (Compile / doc / target).value)
  .settings(inConfig(Test)(Seq(
    reStart / mainClass := (Test / run / mainClass).value, {
      import spray.revolver.Actions._
      reStart := Def
        .inputTask {
          restartApp(
            streams.value,
            reLogTag.value,
            thisProjectRef.value,
            reForkOptions.value,
            (reStart / mainClass).value,
            (reStart / fullClasspath).value,
            reStartArgs.value,
            startArgsParser.parsed)
        }
        .dependsOn((Compile / products))
        .evaluated
    })))

lazy val benchmarks = Project(id = "benchmarks", base = file("benchmarks"))
  .dependsOn(runtime)
  .enablePlugins(JmhPlugin)
  .disablePlugins(MimaPlugin, CiReleasePlugin)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head,
    (publish / skip) := true)

// Config to allow only building scaladocs for runtime module but in/from the docs module
val AkkaGrpcRuntime = config("akkaGrpcRuntime")

lazy val docs = Project(id = "akka-grpc-docs", base = file("docs"))
// Make sure code generation is ran:
  .dependsOn(pluginTesterScala)
  .dependsOn(pluginTesterJava)
  .enablePlugins(
    SitePreviewPlugin,
    AkkaParadoxPlugin,
    ParadoxSitePlugin,
    PreprocessPlugin,
    PublishRsyncPlugin,
    SiteScaladocPlugin)
  .disablePlugins(MimaPlugin, CiReleasePlugin)
  .settings(
    name := "Akka gRPC",
    publish / skip := true,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/akka-grpc/${projectInfoVersion.value}",
    Paradox / siteSubdirName := s"libraries/akka-grpc/${projectInfoVersion.value}",
    // Make sure code generation is ran before paradox:
    (Compile / paradox) := (Compile / paradox).dependsOn(Compile / compile).value,
    paradoxGroups := Map("Language" -> Seq("Java", "Scala"), "Buildtool" -> Seq("sbt", "Gradle", "Maven")),
    paradoxRoots := List("index.html", "quickstart-java/index.html", "quickstart-scala/index.html"),
    Compile / paradoxProperties ++= Map(
      "akka.version" -> Dependencies.Versions.akka,
      "akka-http.version" -> Dependencies.Versions.akkaHttp,
      "grpc.version" -> Dependencies.Versions.grpc,
      "project.url" -> "https://doc.akka.io/libraries/akka-grpc/current/",
      "canonical.base_url" -> "https://doc.akka.io/libraries/akka-grpc/current",
      "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/current/",
      // Akka
      "extref.akka.base_url" -> s"https://doc.akka.io/libraries/akka-core/${Dependencies.Versions.akkaBinary}/%s",
      "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${Dependencies.Versions.akkaBinary}",
      "javadoc.akka.base_url" -> s"https://doc.akka.io/japi/akka/${Dependencies.Versions.akkaBinary}/",
      // Akka HTTP
      "extref.akka-http.base_url" -> s"https://doc.akka.io/libraries/akka-http/${Dependencies.Versions.akkaHttpBinary}/%s",
      "scaladoc.akka.http.base_url" -> s"https://doc.akka.io/api/akka-http/${Dependencies.Versions.akkaHttpBinary}/",
      "javadoc.akka.http.base_url" -> s"https://doc.akka.io/japi/akka-http/${Dependencies.Versions.akkaHttpBinary}/",
      // Akka Management
      "extref.akka-management.base_url" -> s"https://doc.akka.io/libraries/akka-management/current/%s",
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
  .settings(
    // only the publish API docs for the runtime, inlined instead of using SiteScaladocPlugin.scaladocSettings
    // to be able to reference the `projectInfoVersion` in the sub dir path
    inConfig(AkkaGrpcRuntime)(
      Seq(
        siteSubdirName := s"api/akka-grpc/${projectInfoVersion.value}",
        mappings := (runtime / Compile / packageDoc / mappings).value)) ++
    SiteHelpers.addMappingsToSiteDir(AkkaGrpcRuntime / mappings, AkkaGrpcRuntime / siteSubdirName))

lazy val pluginTesterScala = Project(id = "akka-grpc-plugin-tester-scala", base = file("plugin-tester-scala"))
  .disablePlugins(MimaPlugin, CiReleasePlugin)
  .settings(Dependencies.pluginTester)
  .settings(
    (publish / skip) := true,
    fork := true,
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head,
    Test / parallelExecution := false,
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("flat_package", "server_power_apis"))
  .pluginTestingSettings

lazy val pluginTesterJava = Project(id = "akka-grpc-plugin-tester-java", base = file("plugin-tester-java"))
  .disablePlugins(MimaPlugin, CiReleasePlugin)
  .settings(Dependencies.pluginTester)
  .settings(
    (publish / skip) := true,
    fork := true,
    PB.protocVersion := Dependencies.Versions.googleProtobuf,
    ReflectiveCodeGen.generatedLanguages := Seq("Java"),
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head,
    Test / parallelExecution := false,
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("server_power_apis"))
  .pluginTestingSettings

lazy val pluginTesterSdkHandler =
  Project(id = "akka-grpc-plugin-tester-sdk-handler", base = file("plugin-tester-sdk-handler"))
    .disablePlugins(MimaPlugin, CiReleasePlugin)
    .settings(Dependencies.pluginTester)
    .settings(
      (publish / skip) := true,
      fork := true,
      PB.protocVersion := Dependencies.Versions.googleProtobuf,
      ReflectiveCodeGen.generatedLanguages := Seq("Java"),
      crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
      scalaVersion := Dependencies.Versions.CrossScalaForLib.head,
      Test / parallelExecution := false,
      ReflectiveCodeGen.codeGeneratorSettings ++= Seq("generate_scala_handler_factory"))
    .pluginTestingSettings

lazy val root = Project(id = "akka-grpc", base = file("."))
  .disablePlugins(SitePlugin, MimaPlugin, CiReleasePlugin)
  .aggregate(
    runtime,
    codegen,
    mavenPlugin,
    sbtPlugin,
    scalapbProtocPlugin,
    interopTests,
    pluginTesterScala,
    pluginTesterJava,
    pluginTesterSdkHandler,
    benchmarks,
    docs)
  .settings(
    (publish / skip) := true,
    (Compile / headerCreate / unmanagedSources) := (baseDirectory.value / "project").**("*.scala").get,
    // https://github.com/sbt/sbt/issues/3465
    // Libs and plugins must share a version. The root project must use that
    // version (and set the crossScalaVersions as empty list) so each sub-project
    // can then decide which scalaVersion and crossCalaVersions they use.
    crossScalaVersions := Nil,
    scalaVersion := scala212)
