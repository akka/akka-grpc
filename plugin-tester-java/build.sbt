import akka.grpc.gen.javadsl.JavaBothCodeGenerator
import protocbridge.Target

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime"

//#protoSources
inConfig(Compile)(Seq(
  PB.protoSources += new File("src/main/proto"),
  //#protoSources
  // does not seem to work :( added a symlink for now.
  akkaGrpcCodeGenerators := GeneratorAndSettings(JavaBothCodeGenerator) :: Nil,
  akkaGrpcModelGenerators := Seq[Target](PB.gens.java -> sourceManaged.value),
//#protoSources
))
//#protoSources

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )

val grpcVersion = "1.11.0"

// for loading of cert, issue #89
libraryDependencies += "io.grpc" % "grpc-testing" % grpcVersion
