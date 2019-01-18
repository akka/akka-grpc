package akka.grpc

import sbt._
import sbt.Keys._

object Dependencies {

  object Versions {
    val scala211 = "2.11.12"
    val scala212 = "2.12.8"

    val akka = "2.5.19"
    val akkaHttp = "10.1.5"

    val play = "2.7.0-RC3"

    val grpc = "1.16.1" // checked synced by GrpcVersionSyncCheckPlugin
    val config = "1.3.3"
    val sslConfig = "0.3.6"

    val scalaTest = "3.0.5"

    val maven = "3.5.4"
  }

  object Compile {
    val akkaStream       = "com.typesafe.akka"            %% "akka-stream"        % Versions.akka
    val akkaHttp         = "com.typesafe.akka"            %% "akka-http"          % Versions.akkaHttp
    val akkaHttpCore     = "com.typesafe.akka"            %% "akka-http-core"     % Versions.akkaHttp
    val akkaHttp2Support = "com.typesafe.akka"            %% "akka-http2-support" % Versions.akkaHttp
    val akkaDiscovery    = "com.typesafe.akka"            %% "akka-discovery"     % Versions.akka

    val scalapbCompilerPlugin = "com.thesamet.scalapb" %% "compilerplugin"  % scalapb.compiler.Version.scalapbVersion
    val scalapbRuntime        = "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion exclude("io.grpc", "grpc-netty")

    val grpcCore           = "io.grpc" % "grpc-core"            % Versions.grpc
    val grpcStub           = "io.grpc" % "grpc-stub"            % Versions.grpc
    val grpcNettyShaded    = "io.grpc" % "grpc-netty-shaded"    % Versions.grpc

    // Excluding grpc-alts works around a complex resolution bug
    // Details are in https://github.com/akka/akka-grpc/pull/469
    val grpcInteropTesting = "io.grpc" % "grpc-interop-testing" % Versions.grpc exclude("io.grpc", "grpc-alts")

    val config = "com.typesafe" % "config" % Versions.config
    val sslConfigCore = "com.typesafe" %% "ssl-config-core" % Versions.sslConfig

    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.25"
    val mavenPluginApi = "org.apache.maven" % "maven-plugin-api" % Versions.maven // Apache v2
    val mavenCore = "org.apache.maven" % "maven-core" % Versions.maven // Apache v2
    val protocJar = "com.github.os72" % "protoc-jar" % "3.5.1"

    val plexusBuildApi = "org.sonatype.plexus" % "plexus-build-api" % "0.0.7" % "optional"// Apache v2

    val play = "com.typesafe.play" %% "play" % Versions.play exclude("javax.activation", "javax.activation-api")  // Apache V2 (exclusion is "either GPL or CDDL")
    val playAkkaHttpServer = "com.typesafe.play" %% "play-akka-http-server" % Versions.play // Apache V2
  }

  object Test {
    final val Test = sbt.Test
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // Apache V2
    val junit = "junit" % "junit" % "4.12" % "test" // Common Public License 1.0
    val akkaDiscoveryConfig    = "com.typesafe.akka" %% "akka-discovery"     % Versions.akka % "test"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test"
  }

  object Plugins {
    val sbtProtoc = "com.thesamet" % "sbt-protoc" % "0.99.18"
  }

  private val l = libraryDependencies

  val testing = Seq(
    Test.scalaTest,
    Test.junit
  )

  val codegen = l ++= Seq(
    Compile.scalapbCompilerPlugin,
    Compile.scalapbRuntime
  ) ++ testing

  val runtime = l ++= Seq(
    Compile.scalapbRuntime,

    Compile.grpcCore,
    Compile.grpcStub % "provided", // comes from the generators
    Compile.grpcNettyShaded,
    // 'config' is also a transitive dependency, but Maven will select an old
    // version coming in via `sslConfigCore` unless we explicitly add a
    // dependency on the newer version here.
    Compile.config,
    Compile.sslConfigCore,
    Compile.akkaStream,
    Compile.akkaHttpCore,
    Compile.akkaHttp,
    Compile.akkaHttp2Support,
    Compile.akkaDiscovery,
    // these two are available when used through Play, which is also the only case when they are needed
    Compile.play % "provided",
    Compile.playAkkaHttpServer % "provided",
    Test.akkaDiscoveryConfig,
    Test.akkaTestkit,
  ) ++ testing

  val mavenPlugin = l ++= Seq(
    Compile.slf4jApi,
    Compile.mavenPluginApi,
    Compile.mavenCore,
    Compile.protocJar,
    Compile.plexusBuildApi,
  ) ++ testing

  val sbtPlugin = Seq(
    l += Compile.scalapbCompilerPlugin,
    // we depend on it in the settings of the plugin since we set keys of the sbt-protoc plugin
    addSbtPlugin(Plugins.sbtProtoc),
  )

  val interopTests = l ++= Seq(
    Compile.grpcInteropTesting,
    Compile.grpcInteropTesting % "protobuf", // gets the proto files for interop tests
    Compile.akkaHttp,
    Compile.play,
    Compile.playAkkaHttpServer,
  ) ++ testing.map(_.withConfigurations(Some("compile")))

  val pluginTester = l++= Seq(
    // usually automatically added by `suggestedDependencies`, which doesn't work with ReflectiveCodeGen
    Compile.grpcStub,
  ) ++ testing
}
