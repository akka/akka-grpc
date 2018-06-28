package akka.grpc

import sbt._
import sbt.Keys._

object Dependencies {

  object Versions {
    val akka = "2.5.12"
    val akkaHttp = "10.1.3"

    val scalapb = "0.7.1"
    val grpc = "1.13.1"

    val scalaTest = "3.0.4"
    val scalaJava8Compat = "0.8.0"

    val maven = "3.5.3"
  }

  object Compile {
    val akkaStream       = "com.typesafe.akka" %% "akka-stream"        % Versions.akka
    val akkaHttp         = "com.typesafe.akka" %% "akka-http"          % Versions.akkaHttp
    val akkaHttpCore     = "com.typesafe.akka" %% "akka-http-core"     % Versions.akkaHttp
    val akkaHttp2Support = "com.typesafe.akka" %% "akka-http2-support" % Versions.akkaHttp

    val scalapbCompilerPlugin = "com.thesamet.scalapb" %% "compilerplugin"  % Versions.scalapb
    val scalapbRuntime        = "com.thesamet.scalapb" %% "scalapb-runtime" % Versions.scalapb exclude("io.grpc", "grpc-netty")

    val grpcCore           = "io.grpc" % "grpc-core"            % Versions.grpc
    val grpcStub           = "io.grpc" % "grpc-stub"            % Versions.grpc
    val grpcNettyShaded    = "io.grpc" % "grpc-netty-shaded"    % Versions.grpc
    val grpcInteropTesting = "io.grpc" % "grpc-interop-testing" % Versions.grpc

    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.25"
    val mavenPluginApi = "org.apache.maven" % "maven-plugin-api" % Versions.maven // Apache v2
    val mavenCore = "org.apache.maven" % "maven-core" % Versions.maven // Apache v2
    val protocJar = "com.github.os72" % "protoc-jar" % "3.5.1"

    val plexusBuildApi = "org.sonatype.plexus" % "plexus-build-api" % "0.0.7" % "optional"// Apache v2
  }

  object Agents {
    val jettyAlpnAgent = "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7"
  }

  object Test {
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // ApacheV2
    val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % Versions.scalaJava8Compat % "test" // BSD 3-clause
  }

  object Plugins {
    val sbtProtoc = "com.thesamet" % "sbt-protoc" % "0.99.18"
  }

  private val l = libraryDependencies

  val testing = Seq(
    Test.scalaTest
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
    Compile.akkaStream,
    Compile.akkaHttpCore,
    Compile.akkaHttp,
    Compile.akkaHttp2Support
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
    Test.scalaJava8Compat
  ) ++ testing.map(_.withConfigurations(Some("compile")))
}
