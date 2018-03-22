import akka.grpc.gen.javadsl.JavaServerCodeGenerator
import akka.grpc.gen.scaladsl.ScalaBothCodeGenerator
import protocbridge.Target

organization := "com.lightbend.akka.grpc"

// For the akka-http snapshot
resolvers += Resolver.bintrayRepo("akka", "maven")

libraryDependencies ++= Seq(
  "io.grpc"                  % "grpc-interop-testing"    % "1.10.0"                     % "protobuf",
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
  "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "test"
)

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

// By default we enable the 'flat_package' option by default to get package names
// that are more consistent between Scala and Java.
// Because the interop tests generate both Scala and Java code, however, here we disable this
// option to avoid name clashes in the generated classes:
(akkaGrpcCodeGeneratorSettings in Compile) := (akkaGrpcCodeGeneratorSettings in Compile).value.filterNot(_ == "flat_package")

// proto files from "io.grpc" % "grpc-interop-testing" contain duplicate Empty definitions;
// * google/protobuf/empty.proto
// * io/grpc/testing/integration/empty.proto
// They have different "java_outer_classname" options, but scalapb does not look at it:
// https://github.com/scalapb/ScalaPB/issues/243#issuecomment-279769902
// Therefore we exclude it here.
excludeFilter in PB.generate := new SimpleFileFilter(
  (f: File) => f.getAbsolutePath.endsWith("google/protobuf/empty.proto"))

(akkaGrpcCodeGenerators in Compile) := Seq(
  GeneratorAndSettings(JavaServerCodeGenerator, (akkaGrpcCodeGeneratorSettings in Compile).value),
  GeneratorAndSettings(ScalaBothCodeGenerator, (akkaGrpcCodeGeneratorSettings in Compile).value))
(akkaGrpcModelGenerators in Compile) += PB.gens.java -> sourceManaged.value
