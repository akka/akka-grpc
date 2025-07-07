scalaVersion := "2.13.15"

resolvers += "Akka library repository".at("https://repo.akka.io/maven/github_actions/github_actions")

organization := "com.lightbend.akka.grpc"

val grpcVersion = "1.73.0" // checked synced by VersionSyncCheckPlugin

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-interop-testing" % grpcVersion % "protobuf-src",
  "com.lightbend.akka.grpc" %% "akka-grpc-interop-tests" % sys.props("project.version") % "test",
  "org.scalatest" %% "scalatest" % "3.2.12" % "test" // ApacheV2
)

scalacOptions ++= List("-unchecked", "-deprecation", "-language:_", "-encoding", "UTF-8")

enablePlugins(AkkaGrpcPlugin)

// proto files from "io.grpc" % "grpc-interop-testing" contain duplicate Empty definitions;
// * google/protobuf/empty.proto
// * io/grpc/testing/integration/empty.proto
// They have different "java_outer_classname" options, but scalapb does not look at it:
// https://github.com/scalapb/ScalaPB/issues/243#issuecomment-279769902
// Therefore we exclude it here.
// FIXME descriptor.proto is excluded because of EnumType issue https://github.com/scalapb/ScalaPB/issues/1557
PB.generate / excludeFilter := new SimpleFileFilter((f: File) =>
  f.getAbsolutePath.endsWith("google/protobuf/descriptor.proto") ||
  f.getAbsolutePath.endsWith("google/protobuf/empty.proto") ||
  // grpc-interop pulls in proto files with unfulfilled transitive deps it seems, so skip them as well
  f.getParent.contains("envoy"))

//#sources-both
// This is the default - both client and server
akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client, AkkaGrpc.Server)

//#sources-both

/**
 * //#sources-client
 * // only client
 * akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client)
 *
 * //#sources-client
 *
 * //#sources-server
 * // only server
 * akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server)
 * //#sources-server
 *
 * //#languages-scala
 * // default is Scala only
 * akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala)
 *
 * //#languages-scala
 *
 * //#languages-java
 * // Java only
 * akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)
 *
 * //#languages-java
 */

//#languages-both
// Generate both Java and Scala API's.
// By default the 'flat_package' option is enabled so that generated
// package names are consistent between Scala and Java.
// With both languages enabled we disable that option to avoid name conflicts
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala, AkkaGrpc.Java)
akkaGrpcCodeGeneratorSettings := akkaGrpcCodeGeneratorSettings.value.filterNot(_ == "flat_package")
//#languages-both
