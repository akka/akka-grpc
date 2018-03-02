package akka

import sbt._
import sbt.Keys._

object Dependencies {

  object Versions {
    val akka = "2.5.11"
    // snapshot from https://github.com/akka/akka-http/pull/1882
    val akkaHttp = "10.1.0-RC2+19-8e20bb26"

    val scalapb = "0.6.7"
    val grpc = "1.10.0"

    val scalaTest = "3.0.4"
  }

  object Compile {
    val akkaStream       = "com.typesafe.akka" %% "akka-stream"        % Versions.akka
    val akkaHttp         = "com.typesafe.akka" %% "akka-http"          % Versions.akkaHttp
    val akkaHttpCore     = "com.typesafe.akka" %% "akka-http-core"     % Versions.akkaHttp
    val akkaHttp2Support = "com.typesafe.akka" %% "akka-http2-support" % Versions.akkaHttp

    val scalapbCompilerPlugin = "com.trueaccord.scalapb" %% "compilerplugin"  % Versions.scalapb
    val scalapbRuntime        = "com.trueaccord.scalapb" %% "scalapb-runtime" % Versions.scalapb exclude("io.grpc", "grpc-netty")

    val grpcCore           = "io.grpc" % "grpc-core"            % Versions.grpc
    val grpcNetty          = "io.grpc" % "grpc-netty-shaded"    % Versions.grpc
    val grpcInteropTesting = "io.grpc" % "grpc-interop-testing" % Versions.grpc
  }

  object Agents {
    val jettyAlpnAgent = "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7"
  }

  object Test {
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // ApacheV2
  }

  object Plugins {
    val sbtProtoc = "com.thesamet" % "sbt-protoc" % "0.99.12"
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
    Compile.grpcNetty,
    Compile.akkaStream,
    Compile.akkaHttpCore,
    Compile.akkaHttp2Support
  ) ++ testing

  val sbtPlugin = Seq(
    l += Compile.scalapbCompilerPlugin,
    // we depend on it in the settings of the plugin since we set keys of the sbt-protoc plugin
    addSbtPlugin(Plugins.sbtProtoc),
  )

  val interopTests = l ++= Seq(
    Compile.grpcInteropTesting
  ) ++ testing.map(_.withConfigurations(Some("compile")))
}
