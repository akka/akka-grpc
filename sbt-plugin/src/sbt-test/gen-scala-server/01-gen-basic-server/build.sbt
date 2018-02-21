//PB.targets in Compile := Seq(
//  scalapb.gen(grpc = false) -> (sourceManaged in Compile).value
//)

enablePlugins(JavaAgent)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime"

// TODO how not to have to depend on all that but have the needed things included for you automatically

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.9",
  "io.grpc" % "grpc-core"  % com.trueaccord.scalapb.compiler.Version.grpcJavaVersion,
  "io.grpc" % "grpc-netty" % com.trueaccord.scalapb.compiler.Version.grpcJavaVersion,
  "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion,
)

// ---------- test checks --------------


TaskKey[Unit]("check") := {
  import scala.sys.process._
  "find . ".!
}
