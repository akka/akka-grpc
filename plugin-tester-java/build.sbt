enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime"

// generate both client and server (default) in Java
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )

javacOptions in compile += "-Xlint:deprecation"
