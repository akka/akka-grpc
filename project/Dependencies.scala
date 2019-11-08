package akka.grpc

import sbt._
import sbt.Keys._

object Dependencies {
  object Versions {
    val scala212 = "2.12.10"
    val scala213 = "2.13.1"

    val akka = "2.5.25"
    val akkaHttp = "10.1.10"

    val grpc = "1.25.0" // checked synced by GrpcVersionSyncCheckPlugin
    val config = "1.3.4"
    // We should follow Akka in whether to update to 0.4.0
    // https://github.com/akka/akka/issues/27142#issuecomment-503146305
    val sslConfig = "0.3.8"

    val scalaTest = "3.0.8"

    val maven = "3.6.2"
  }

  object Compile {
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % Versions.akka
    val akkaHttp = "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp
    val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % Versions.akkaHttp
    val akkaHttp2Support = "com.typesafe.akka" %% "akka-http2-support" % Versions.akkaHttp
    val akkaDiscovery = "com.typesafe.akka" %% "akka-discovery" % Versions.akka
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Versions.akka

    val scalapbCompilerPlugin = "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
    val scalapbRuntime = ("com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion)
      .exclude("io.grpc", "grpc-netty")

    val grpcCore = "io.grpc" % "grpc-core" % Versions.grpc
    val grpcStub = "io.grpc" % "grpc-stub" % Versions.grpc
    val grpcNettyShaded = "io.grpc" % "grpc-netty-shaded" % Versions.grpc

    // Excluding grpc-alts works around a complex resolution bug
    // Details are in https://github.com/akka/akka-grpc/pull/469
    val grpcInteropTesting = ("io.grpc" % "grpc-interop-testing" % Versions.grpc).exclude("io.grpc", "grpc-alts")

    val config = "com.typesafe" % "config" % Versions.config
    val sslConfigCore = "com.typesafe" %% "ssl-config-core" % Versions.sslConfig

    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.29"
    val mavenPluginApi = "org.apache.maven" % "maven-plugin-api" % Versions.maven // Apache v2
    val mavenCore = "org.apache.maven" % "maven-core" % Versions.maven // Apache v2
    val protocJar = "com.github.os72" % "protoc-jar" % "3.8.0"

    val plexusBuildApi = "org.sonatype.plexus" % "plexus-build-api" % "0.0.7" % "optional" // Apache v2
  }

  object Test {
    final val Test = sbt.Test
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // Apache V2
    val junit = "junit" % "junit" % "4.12" % "test" // Common Public License 1.0
    val akkaDiscoveryConfig = "com.typesafe.akka" %% "akka-discovery" % Versions.akka % "test"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test"
  }

  object Runtime {
    val logback = "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime" // Eclipse 1.0
  }

  object Plugins {
    val sbtProtoc = "com.thesamet" % "sbt-protoc" % "0.99.26"
  }

  private val l = libraryDependencies

  val testing = Seq(Test.scalaTest, Test.junit)

  val codegen = l ++= Seq(Compile.scalapbCompilerPlugin, Compile.scalapbRuntime) ++ testing

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
        Test.akkaDiscoveryConfig,
        Test.akkaTestkit) ++ testing

  val mavenPlugin = l ++= Seq(
        Compile.slf4jApi,
        Compile.mavenPluginApi,
        Compile.mavenCore,
        Compile.protocJar,
        Compile.plexusBuildApi) ++ testing

  val sbtPlugin = Seq(
    l += Compile.scalapbCompilerPlugin,
    // we depend on it in the settings of the plugin since we set keys of the sbt-protoc plugin
    addSbtPlugin(Plugins.sbtProtoc))

  val interopTests = l ++= Seq(
        Compile.grpcInteropTesting,
        Compile.grpcInteropTesting % "protobuf", // gets the proto files for interop tests
        Compile.akkaHttp,
        Compile.akkaSlf4j,
        Runtime.logback) ++ testing.map(_.withConfigurations(Some("compile")))

  val pluginTester = l ++= Seq(
        // usually automatically added by `suggestedDependencies`, which doesn't work with ReflectiveCodeGen
        Compile.grpcStub) ++ testing
}
