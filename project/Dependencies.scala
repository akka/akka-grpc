package akka.grpc

import sbt._
import sbt.Keys._
import buildinfo.BuildInfo

object Dependencies {
  object Versions {
    val scala212 = "2.12.18"
    val scala213 = "2.13.11"
    val scala3 = "3.3.1"

    // the order in the list is important because the head will be considered the default.
    val CrossScalaForLib = Seq(scala213, scala3)
    val CrossScalaForPlugin = Seq(scala212)

    // We don't force Akka updates because downstream projects can upgrade
    // themselves. For more information see
    // https://doc.akka.io//docs/akka/current/project/downstream-upgrade-strategy.html
    val akka = "2.9.0-M3"
    val akkaBinary = "2.8"
    val akkaHttp = "10.6.0-M2"
    val akkaHttpBinary = "10.6"

    val grpc = "1.58.0" // checked synced by VersionSyncCheckPlugin
    // FIXME should be same as `grpc` but due to https://github.com/scalapb/ScalaPB/issues/1557
    // we are testing with an older version that doesn't expose that problem.
    val grpcInterop = "1.54.2"

    // Even referenced explicitly in the sbt-plugin's sbt-tests
    // If changing this, remember to update protoc plugin version to align in
    // maven-plugin/src/main/maven/plugin.xml and akka.grpc.sbt.AkkaGrpcPlugin
    val googleProtobuf = "3.24.0" // checked synced by VersionSyncCheckPlugin
    val googleApi = "2.23.0"

    val scalaTest = "3.2.12"

    val maven = "3.9.4"
  }

  object Compile {
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % Versions.akka
    val akkaPki = "com.typesafe.akka" %% "akka-pki" % Versions.akka

    val akkaHttp = "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp
    val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % Versions.akkaHttp
    val akkaDiscovery = "com.typesafe.akka" %% "akka-discovery" % Versions.akka
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Versions.akka

    val akkaHttpCors = ("ch.megard" %% "akka-http-cors" % "1.2.0") // Apache v2
      .excludeAll(ExclusionRule(organization = "com.typesafe.akka")) // needed to not pull in 2.13 deps for Scala 3

    val scalapbCompilerPlugin = "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
    val scalapbRuntime = ("com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion)
      .exclude("io.grpc", "grpc-netty")

    val grpcCore = "io.grpc" % "grpc-core" % Versions.grpc
    val grpcStub = "io.grpc" % "grpc-stub" % Versions.grpc
    val grpcNettyShaded = "io.grpc" % "grpc-netty-shaded" % Versions.grpc
    val grpcProtobuf = "io.grpc" % "grpc-protobuf" % Versions.grpc

    // Excluding grpc-alts works around a complex resolution bug
    // Details are in https://github.com/akka/akka-grpc/pull/469
    val grpcInteropTesting = ("io.grpc" % "grpc-interop-testing" % Versions.grpcInterop)
      .exclude("io.grpc", "grpc-alts")
      .exclude("io.grpc", "grpc-xds")

    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.36"
    val mavenPluginApi = "org.apache.maven" % "maven-plugin-api" % Versions.maven // Apache v2
    val mavenCore = "org.apache.maven" % "maven-core" % Versions.maven // Apache v2
    val protocJar = "com.github.os72" % "protoc-jar" % "3.11.4"

    val plexusBuildApi = "org.sonatype.plexus" % "plexus-build-api" % "0.0.7" % "optional" // Apache v2
  }

  object Test {
    final val Test = sbt.Test
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // Apache V2
    val scalaTestPlusJunit = "org.scalatestplus" %% "junit-4-13" % (Versions.scalaTest + ".0") % "test" // Apache V2
    val akkaDiscoveryConfig = "com.typesafe.akka" %% "akka-discovery" % Versions.akka % "test"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test"
    val akkaTestkitTyped = "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akka % "test"
    val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % Versions.akka % "test"
  }

  object Runtime {
    val logback = "ch.qos.logback" % "logback-classic" % "1.2.12" % "runtime" // Eclipse 1.0
  }

  object Protobuf {
    val protobufJava = "com.google.protobuf" % "protobuf-java" % Versions.googleProtobuf
    val googleCommonProtos = "com.google.protobuf" % "protobuf-java" % Versions.googleProtobuf % "protobuf"

  }

  object GrpcApi {
    val googleApiProtos = "com.google.api.grpc" % "proto-google-common-protos" % Versions.googleApi % "protobuf"
  }

  object Plugins {
    val sbtProtoc = "com.thesamet" % "sbt-protoc" % BuildInfo.sbtProtocVersion
  }

  private val l = libraryDependencies

  val codegen = l ++= Seq(
    Compile.scalapbCompilerPlugin,
    Protobuf.protobufJava, // or else scalapb pulls older version in transitively
    Compile.grpcProtobuf,
    Test.scalaTest)

  val runtime = l ++= Seq(
    Compile.scalapbRuntime,
    Protobuf.protobufJava, // or else scalapb pulls older version in transitively
    Compile.grpcProtobuf,
    Compile.grpcCore,
    Compile.grpcStub % "provided", // comes from the generators
    Compile.grpcNettyShaded,
    Compile.akkaStream,
    Compile.akkaHttpCore,
    Compile.akkaHttp,
    Compile.akkaDiscovery,
    Compile.akkaHttpCors % "provided",
    Test.akkaTestkit,
    Test.akkaStreamTestkit,
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
    l ++= Seq(Compile.scalapbCompilerPlugin),
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
    Test.akkaTestkit,
    Test.akkaStreamTestkit)

  val pluginTester = l ++= Seq(
    // usually automatically added by `suggestedDependencies`, which doesn't work with ReflectiveCodeGen
    Compile.grpcStub,
    Compile.akkaPki,
    Compile.akkaHttpCors,
    Runtime.logback,
    Test.scalaTest,
    Test.scalaTestPlusJunit,
    Test.akkaTestkitTyped,
    GrpcApi.googleApiProtos)
}
