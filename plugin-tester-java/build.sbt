import akka.http.grpc.JavaServerCodeGenerator
import protocbridge.Target

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

inConfig(Compile)(Seq(
  // does not seem to work :( added a symlink for now.
  PB.includePaths += new File("src/main/proto"),
  akkaGrpcCodeGenerators := GeneratorAndSettings(JavaServerCodeGenerator) :: Nil,
  akkaGrpcModelGenerators := Seq[Target](PB.gens.java -> sourceManaged.value),
))

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime"

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )

