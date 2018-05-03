enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime"

//#protoSources
// "sourceDirectory in Compile" is "src/main", so this adds "src/main/proto":
inConfig(Compile)(Seq(
  PB.protoSources += sourceDirectory.value / "proto"
))
//#protoSources

// generate stubs for both client and server (default) in Java
akkaGrpcTargetLanguages := Seq(AkkaGrpc.Java)

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )

// for loading of cert, issue #89
val grpcVersion = "1.11.0"
libraryDependencies += "io.grpc" % "grpc-testing" % grpcVersion
