import akka.grpc.gen.scaladsl.ScalaBothCodeGenerator

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime"

inConfig(Compile)(Seq(
  akkaGrpcCodeGenerators := GeneratorAndSettings(ScalaBothCodeGenerator, (akkaGrpcCodeGeneratorSettings in Compile).value) :: Nil
))

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )

val grpcVersion = "1.11.0"

// for loading of cert, issue #89
libraryDependencies += "io.grpc" % "grpc-testing" % grpcVersion
