import akka.grpc.gen.scaladsl.ScalaBothCodeGenerator

enablePlugins(AkkaGrpcPlugin)

//#alpn
enablePlugins(JavaAgent)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime"
//#alpn

inConfig(Compile)(Seq(
  akkaGrpcCodeGenerators := GeneratorAndSettings(ScalaBothCodeGenerator, (akkaGrpcCodeGeneratorSettings in Compile).value) :: Nil
))

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )

val grpcVersion = "1.11.0"
