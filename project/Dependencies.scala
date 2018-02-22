package akka

import sbt._
import sbt.Keys._

object Dependencies {

  object Versions {
    val akka = "2.5.9"
    // snapshot from master
    val akkaHttp = "10.1.0-RC2+15-f80d1fe5"

    val scalapb = "0.6.7"
    val grpc = "1.10.0"

    val scalaTest = "3.0.4"
  }

  object Compile {
    val akkaStream       = "com.typesafe.akka" %% "akka-stream"        % Versions.akka
    val akkaHttp         = "com.typesafe.akka" %% "akka-http"          % Versions.akkaHttp
    val akkaHttp2Support ="com.typesafe.akka"  %% "akka-http2-support" % Versions.akkaHttp

    val scalapbCompilerPlugin = "com.trueaccord.scalapb" %% "compilerplugin"       % Versions.scalapb
    val scalapbRuntimeGrpc =    "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % Versions.scalapb

    val grpcCore           = "io.grpc" % "grpc-core"            % Versions.grpc
    val grpcNetty          = "io.grpc" % "grpc-netty"           % Versions.grpc
    val grpcInteropTesting = "io.grpc" % "grpc-interop-testing" % Versions.grpc
  }

  object Test {
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // ApacheV2
  }

  private val l = libraryDependencies

  val testing = Seq(
    Test.scalaTest
  )

  val common = l ++= Seq(
    Compile.scalapbCompilerPlugin,
    Compile.scalapbRuntimeGrpc
  ) ++ testing

  val server = l ++= Seq(
    Compile.scalapbCompilerPlugin,
    Compile.scalapbRuntimeGrpc,

    Compile.grpcCore,
    Compile.grpcNetty,
    Compile.akkaStream,
    Compile.akkaHttp,
    Compile.akkaHttp2Support
  ) ++ testing

  val interopTests = l ++= Seq(
    Compile.grpcInteropTesting
  ) ++ testing.map(_.withConfigurations(Some("compile")))
}
