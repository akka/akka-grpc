package akka.grpc

import sbt._
import sbt.Keys._
import buildinfo.BuildInfo

object Dependencies {
  object Versions {
    val scala212 = "2.12.10"
    val scala213 = "2.13.1"

    val akka = "2.6.2"
    val akkaHttp = "10.1.11"

    val grpc = "1.26.0" // checked synced by GrpcVersionSyncCheckPlugin

    val scalaTest = "3.1.0"

    val maven = "3.6.3"
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

    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.30"
    val mavenPluginApi = "org.apache.maven" % "maven-plugin-api" % Versions.maven // Apache v2
    val mavenCore = "org.apache.maven" % "maven-core" % Versions.maven // Apache v2
    val protocJar = "com.github.os72" % "protoc-jar" % "3.11.1"

    val plexusBuildApi = "org.sonatype.plexus" % "plexus-build-api" % "0.0.7" % "optional" // Apache v2
  }

  object Test {
    final val Test = sbt.Test
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // Apache V2
    val scalaTestPlusJunit = "org.scalatestplus" %% "junit-4-12" % "3.1.0.0" % "test" // Apache V2
    val akkaDiscoveryConfig = "com.typesafe.akka" %% "akka-discovery" % Versions.akka % "test"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test"
  }

  object Runtime {
    val logback = "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime" // Eclipse 1.0
  }

  object Plugins {
    val sbtProtoc = "com.thesamet" % "sbt-protoc" % BuildInfo.sbtProtocVersion
  }

  private val l = libraryDependencies

  val codegen = l ++= Seq(Compile.scalapbCompilerPlugin, Compile.scalapbRuntime, Test.scalaTest)

  val runtime = l ++= Seq(
        Compile.scalapbRuntime,
        Compile.grpcCore,
        Compile.grpcStub % "provided", // comes from the generators
        Compile.grpcNettyShaded,
        Compile.akkaStream,
        Compile.akkaHttpCore,
        Compile.akkaHttp,
        Compile.akkaHttp2Support,
        Compile.akkaDiscovery,
        Test.akkaDiscoveryConfig,
        Test.akkaTestkit,
        Test.scalaTest,
        Test.scalaTestPlusJunit)

  val mavenPlugin = l ++= Seq(
        Compile.slf4jApi,
        Compile.mavenPluginApi,
        Compile.mavenCore,
        Compile.protocJar,
        Compile.plexusBuildApi,
        Test.scalaTest)

  val sbtPlugin = Seq(
    l += Compile.scalapbCompilerPlugin,
    // we depend on it in the settings of the plugin since we set keys of the sbt-protoc plugin
    addSbtPlugin(Plugins.sbtProtoc))

  val interopTests = l ++= Seq(
        Compile.grpcInteropTesting,
        Compile.grpcInteropTesting % "protobuf", // gets the proto files for interop tests
        Compile.akkaHttp,
        Compile.akkaSlf4j,
        Runtime.logback,
        Test.scalaTest.withConfigurations(Some("compile")),
        Test.scalaTestPlusJunit.withConfigurations(Some("compile")),
        Test.akkaTestkit)

  val pluginTester = l ++= Seq(
        // usually automatically added by `suggestedDependencies`, which doesn't work with ReflectiveCodeGen
        Compile.grpcStub,
        Test.scalaTest)
}
