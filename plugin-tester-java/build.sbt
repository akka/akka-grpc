enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime"

//#protoSources
// "sourceDirectory in Compile" is "src/main", so this adds "src/main/proto":
inConfig(Compile)(Seq(
  PB.protoSources += sourceDirectory.value / "proto"
))
//#protoSources

// generate both client and server (default) in Java
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )

javacOptions in compile += "-Xlint:deprecation"
