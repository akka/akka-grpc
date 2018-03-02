import akka.http.grpc.JavaServerCodeGenerator
import protocbridge.Target

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)
//enablePlugins()

inConfig(Compile)(Seq(
  PB.includePaths += new File("/home/aengelen/dev/akka-grpc/plugin-tester-java/src/main/proto"),
  akkaGrpcCodeGenerators := GeneratorAndSettings(JavaServerCodeGenerator) :: Nil,
  akkaGrpcModelGenerators := Seq[Target](PB.gens.java -> sourceManaged.value),
))
// does not seem to work :( added a symlink for now.

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime"

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )

