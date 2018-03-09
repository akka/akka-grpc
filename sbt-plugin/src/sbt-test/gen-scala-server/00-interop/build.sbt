import akka.http.grpc.JavaServerCodeGenerator
import protocbridge.Target

organization := "com.lightbend.akka.grpc"

// For the akka-http snapshot
resolvers += Resolver.bintrayRepo("akka", "maven")

libraryDependencies ++= Seq(
  "com.lightbend.akka.grpc" %% "akka-grpc-interop-tests" % sys.props("project.version") % "test",
  "org.scalatest"           %% "scalatest" % "3.0.4"     % "test" // ApacheV2
  )

scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-encoding", "UTF-8"
)

javaAgents ++= Seq(
  "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "test"
)

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

(akkaGrpcCodeGenerators in Compile) += GeneratorAndSettings(JavaServerCodeGenerator)
(akkaGrpcModelGenerators in Compile) += PB.gens.java -> sourceManaged.value
