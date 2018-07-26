package akka.grpc

import sbt._
import sbt.Keys._

object Dependencies {

  object Versions {
    val akka = "2.5.14"
    val akkaHttp = "10.1.3"
    val akkaDiscovery = "0.15.0"

    val play = "2.7.0-M1" // FIXME: Update to M2

    val scalapb = "0.7.1"
    val grpc = "1.13.1"
    val config = "1.3.3"
    val sslConfig = "0.2.4"

    val scalaTest = "3.0.4"
    val scalaTestPlusPlay = "4.0.0-M1" // FIXME: Update to M2 when we update Play to M2
    val scalaJava8Compat = "0.8.0"

    val maven = "3.5.3"
  }

  object Compile {
    val akkaStream       = "com.typesafe.akka"            %% "akka-stream"        % Versions.akka
    val akkaHttp         = "com.typesafe.akka"            %% "akka-http"          % Versions.akkaHttp
    val akkaHttpCore     = "com.typesafe.akka"            %% "akka-http-core"     % Versions.akkaHttp
    val akkaHttp2Support = "com.typesafe.akka"            %% "akka-http2-support" % Versions.akkaHttp
    val akkaDiscovery    = "com.lightbend.akka.discovery" %% "akka-discovery"     % Versions.akkaDiscovery

    val scalapbCompilerPlugin = "com.thesamet.scalapb" %% "compilerplugin"  % Versions.scalapb
    val scalapbRuntime        = "com.thesamet.scalapb" %% "scalapb-runtime" % Versions.scalapb exclude("io.grpc", "grpc-netty")

    val grpcCore           = "io.grpc" % "grpc-core"            % Versions.grpc
    val grpcStub           = "io.grpc" % "grpc-stub"            % Versions.grpc
    val grpcNettyShaded    = "io.grpc" % "grpc-netty-shaded"    % Versions.grpc
    val grpcInteropTesting = "io.grpc" % "grpc-interop-testing" % Versions.grpc

    val config = "com.typesafe" % "config" % Versions.config
    val sslConfigCore = "com.typesafe" %% "ssl-config-core" % Versions.sslConfig

    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.25"
    val mavenPluginApi = "org.apache.maven" % "maven-plugin-api" % Versions.maven // Apache v2
    val mavenCore = "org.apache.maven" % "maven-core" % Versions.maven // Apache v2
    val protocJar = "com.github.os72" % "protoc-jar" % "3.5.1"

    val plexusBuildApi = "org.sonatype.plexus" % "plexus-build-api" % "0.0.7" % "optional"// Apache v2

    val play = "com.typesafe.play" %% "play" % Versions.play // Apache M2
    val playGuice = "com.typesafe.play" %% "play-guice" % Versions.play  // Apache M2
    val playAkkaHttpServer = "com.typesafe.play" %% "play-akka-http-server" % Versions.play // Apache M2
  }

  object Agents {
    val jettyAlpnAgent = "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7"
  }

  object Test {
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // ApacheV2
    val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % Versions.scalaJava8Compat % "test" // BSD 3-clause
    val junit = "junit" % "junit" % "4.12" % "test" // Common Public License 1.0
    val play = "com.typesafe.play" %% "play-test" % Versions.play // Apache M2
    val scalaTestPlusPlay = "org.scalatestplus.play" %% "scalatestplus-play" % Versions.scalaTestPlusPlay % "test"
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
    Compile.akkaDiscovery
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
    Test.scalaJava8Compat
  ) ++ testing.map(_.withConfigurations(Some("compile")))

  val playInteropTest = l ++= Seq(
    // TODO #193
    Compile.grpcStub,
    Compile.play,
    Compile.playGuice,
    Compile.playAkkaHttpServer,
    Test.play,
    Test.scalaTestPlusPlay
  ) ++ testing.map(_.withConfigurations(Some("compile")))
}
