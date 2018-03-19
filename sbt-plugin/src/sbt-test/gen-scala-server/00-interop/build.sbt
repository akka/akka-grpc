import akka.http.grpc.javadsl.JavaServerCodeGenerator
import akka.http.grpc.scaladsl.ScalaBothCodeGenerator
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

// By default we enable the 'flat_package' option by default to get package names
// that are more consistent between Scala and Java.
// Because the interop tests generate both Scala and Java code, however, here we disable this
// option to avoid name clashes in the generated classes:
(akkaGrpcCodeGeneratorSettings in Compile) := (akkaGrpcCodeGeneratorSettings in Compile).value.filterNot(_ == "flat_package")

(akkaGrpcCodeGenerators in Compile) := Seq(
  GeneratorAndSettings(JavaServerCodeGenerator, (akkaGrpcCodeGeneratorSettings in Compile).value),
  GeneratorAndSettings(ScalaBothCodeGenerator, (akkaGrpcCodeGeneratorSettings in Compile).value))
(akkaGrpcModelGenerators in Compile) += PB.gens.java -> sourceManaged.value
