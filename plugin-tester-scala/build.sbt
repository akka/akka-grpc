import akka.http.grpc.scaladsl.ScalaBothCodeGenerator

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime"

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )
    .settings(

    )

val grpcVersion = "1.10.0"

// for loading of cert, issue #89
libraryDependencies += "io.grpc" % "grpc-testing" % grpcVersion

(akkaGrpcCodeGenerators in Compile) := Seq(GeneratorAndSettings(ScalaBothCodeGenerator))
