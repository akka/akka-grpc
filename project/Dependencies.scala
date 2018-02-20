package akka

import sbt._
import sbt.Keys._

object Dependencies {

  object Versions {
    val scalapb = com.trueaccord.scalapb.compiler.Version.scalapbVersion // TODO 0.6.7 exists already

    val akka = "2.5.9"
    val akkaHttp = "10.1.0-RC2"

    val scalaTest = "3.0.4"
  }


  private val l = libraryDependencies

  val testing = Seq(
    "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // ApacheV2
  )

  val common = l ++= Seq(
    "com.trueaccord.scalapb" %% "compilerplugin" % Versions.scalapb,
    "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % Versions.scalapb
  ) ++ testing

  val server = l ++= Seq(
    "com.trueaccord.scalapb" %% "compilerplugin" % Versions.scalapb,
    "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % Versions.scalapb,

    // FIXME 1.10 is latest
    "io.grpc" % "grpc-core"   % "1.6.1",

    "io.grpc" % "grpc-netty"  % com.trueaccord.scalapb.compiler.Version.grpcJavaVersion,

    "com.typesafe.akka"      %% "akka-stream"        % Versions.akka,
    "com.typesafe.akka"      %% "akka-http"          % Versions.akkaHttp,
    "com.typesafe.akka"      %% "akka-http2-support" % Versions.akkaHttp

  ) ++ testing

  val interopTests = l ++= Seq(
    "io.grpc" % "grpc-interop-testing" % "1.9.0"
  ) ++ testing
}
