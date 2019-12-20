organization := "com.lightbend.akka.grpc"

// For the akka-http snapshot
resolvers += Resolver.bintrayRepo("akka", "maven")

val grpcVersion = "1.26.0" // checked synced by GrpcVersionSyncCheckPlugin

libraryDependencies ++= Seq(
  // Excluding grpc-alts works around a complex resolution bug
  // Details are in https://github.com/akka/akka-grpc/pull/469
  "io.grpc"                  % "grpc-interop-testing"    % grpcVersion                  % "protobuf" exclude("io.grpc", "grpc-alts"),
  "com.lightbend.akka.grpc" %% "akka-grpc-interop-tests" % sys.props("project.version") % "test",
  "org.scalatest"           %% "scalatest"               % "3.0.4"                      % "test" // ApacheV2
  )

scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-encoding", "UTF-8"
)

javaAgents ++= Seq(
  "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "test"
)

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

// proto files from "io.grpc" % "grpc-interop-testing" contain duplicate Empty definitions;
// * google/protobuf/empty.proto
// * io/grpc/testing/integration/empty.proto
// They have different "java_outer_classname" options, but scalapb does not look at it:
// https://github.com/scalapb/ScalaPB/issues/243#issuecomment-279769902
// Therefore we exclude it here.
excludeFilter in PB.generate := new SimpleFileFilter(
  (f: File) => f.getAbsolutePath.endsWith("google/protobuf/empty.proto"))

//#sources-both
// This is the default - both client and server
akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client, AkkaGrpc.Server)

//#sources-both

/**
//#sources-client
// only client
akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client)

//#sources-client

//#sources-server
// only server
akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server)
//#sources-server

//#languages-scala
// default is Scala only
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala)

//#languages-scala

//#languages-java
// Java only
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

//#languages-java

**/

//#languages-both
// Generate both Java and Scala API's.
// By default the 'flat_package' option is enabled so that generated
// package names are consistent between Scala and Java.
// With both languages enabled we disable that option to avoid name conflicts
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala, AkkaGrpc.Java)
akkaGrpcCodeGeneratorSettings := akkaGrpcCodeGeneratorSettings.value.filterNot(_ == "flat_package")
//#languages-both
