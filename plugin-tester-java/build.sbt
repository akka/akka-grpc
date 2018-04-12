import akka.grpc.gen.javadsl.JavaBothCodeGenerator
import protocbridge.Target

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime"

//#protoSources
// sourceDirectory in Compile is "src/main", so this is "src/main/proto":
PB.protoSources in Compile += sourceDirectory.value / "proto"
//#protoSources

inConfig(Compile)(Seq(
  akkaGrpcCodeGenerators := GeneratorAndSettings(JavaBothCodeGenerator) :: Nil,
  akkaGrpcModelGenerators := Seq[Target](PB.gens.java -> sourceManaged.value),
))

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )

val grpcVersion = "1.11.0"

// for loading of cert, issue #89
libraryDependencies += "io.grpc" % "grpc-testing" % grpcVersion
